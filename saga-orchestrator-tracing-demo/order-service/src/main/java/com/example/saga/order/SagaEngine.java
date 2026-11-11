package com.example.saga.order;

import com.example.saga.common.Command;
import com.example.saga.common.CommandType;
import com.example.saga.common.Reply;
import com.example.saga.common.SagaTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The saga state machine + its DB transaction. Every public method is ONE transaction (state + audit row,
 * row-locked) and returns the next {@link Command} to send, or null. It never touches Kafka:
 * {@link SagaOrchestrator} sends the command AFTER this method returns (= after commit), otherwise a fast
 * reply could arrive before the saga row is committed and be dropped as "unknown order".
 * <p>
 * This is a separate bean (not private methods of the orchestrator) so the {@code @Transactional} proxy applies.
 * <pre>
 * STARTED --InventoryReserved--> INVENTORY_RESERVED --PaymentAuthorized--> PAYMENT_AUTHORIZED -> COMPLETED
 *    |                                  |
 *    | InventoryRejected                | PaymentFailed / timeout
 *    v                                  v
 * COMPENSATING --(ReleaseInventory, then VoidPayment if owed; retried w/ backoff)--> COMPENSATED
 * </pre>
 */
@Service
@EnableConfigurationProperties(SagaProperties.class)
public class SagaEngine {

    private static final Logger log = LoggerFactory.getLogger(SagaEngine.class);

    private final SagaRepository repo;
    private final SagaProperties props;

    public SagaEngine(SagaRepository repo, SagaProperties props) {
        this.repo = repo;
        this.props = props;
    }

    // ---------------------------------------------------------------- transactions (called by SagaOrchestrator)

    /** New order -> persist saga in STARTED, return ReserveInventory. Caller's current span is saved as the saga's trace. */
    @Transactional
    public Command begin(UUID orderId, OrderRequest req) {
        Saga s = new Saga();
        s.id = UUID.randomUUID();
        s.orderId = orderId;
        s.sku = req.sku();
        s.qty = req.qty();
        s.amount = req.amount();
        s.state = SagaState.STARTED;
        s.traceId = SagaTracing.currentTraceId();
        s.traceParent = SagaTracing.currentTraceParent();
        repo.insert(s);
        repo.log(s, null, SagaState.STARTED, null, "order accepted", s.traceId);
        log.info("saga {} STARTED sku={} qty={} amount={}", orderId, s.sku, s.qty, s.amount);
        return sendStep(s, CommandType.RESERVE_INVENTORY, false);
    }

    /** Participant replied: the state table. Duplicate / late replies fall through and are ignored. */
    @Transactional
    public Command applyReply(Reply r) {
        UUID orderId = UUID.fromString(r.orderId());
        Saga s = repo.lockByOrderId(orderId).orElse(null);
        if (s == null) {
            log.debug("reply {} for unknown order {}", r.type(), orderId);
            return null;
        }
        switch (r.type()) {
            case INVENTORY_RESERVED -> {
                if (s.state == SagaState.STARTED) {
                    transition(s, SagaState.INVENTORY_RESERVED, CommandType.AUTHORIZE_PAYMENT, "inventory reserved");
                    return sendStep(s, CommandType.AUTHORIZE_PAYMENT, false);
                }
            }
            case INVENTORY_REJECTED -> {
                if (s.state == SagaState.STARTED) {
                    // nothing to undo: reserve never succeeded
                    return compensate(s, false, false, r.reason());
                }
            }
            case PAYMENT_AUTHORIZED -> {
                if (s.state == SagaState.INVENTORY_RESERVED) {
                    transition(s, SagaState.PAYMENT_AUTHORIZED, null, "payment authorized");
                    transition(s, SagaState.COMPLETED, null, "saga completed");
                    s.nextActionAt = null;
                    repo.update(s);
                    return null;
                }
            }
            case PAYMENT_FAILED -> {
                if (s.state == SagaState.INVENTORY_RESERVED) {
                    // payment never happened -> only the inventory must be undone
                    return compensate(s, true, false, r.reason());
                }
            }
            case INVENTORY_RELEASED -> {
                if (s.state == SagaState.COMPENSATING && s.needsRelease) {
                    s.needsRelease = false;
                    return nextCompensation(s);
                }
            }
            case PAYMENT_VOIDED -> {
                if (s.state == SagaState.COMPENSATING && s.needsVoid) {
                    s.needsVoid = false;
                    return nextCompensation(s);
                }
            }
        }
        // duplicate / late reply (e.g. PaymentAuthorized arriving after we timed out): state machine ignores it
        log.debug("ignored {} in state {}", r.type(), s.state);
        return null;
    }

    /** Step timeout or compensation retry fired for this saga. Re-checks under the row lock (a reply may have won the race). */
    @Transactional
    public Command handleDue(UUID orderId) {
        Saga s = repo.lockByOrderId(orderId).orElseThrow();
        if (s.nextActionAt == null || s.nextActionAt.isAfter(Instant.now())) {
            return null; // handled meanwhile by a reply
        }
        return switch (s.state) {
            case STARTED -> compensate(s, true, false, "timeout waiting for inventory");
            case INVENTORY_RESERVED -> compensate(s, true, true, "timeout waiting for payment");
            case COMPENSATING -> retryCompensation(s);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- compensation

    /** Enter COMPENSATING, remembering which undo actions are still owed. */
    private Command compensate(Saga s, boolean release, boolean voidPayment, String reason) {
        s.needsRelease = release;
        s.needsVoid = voidPayment;
        s.attempts = 0;
        transition(s, SagaState.COMPENSATING, null, reason);
        return nextCompensation(s);
    }

    /** Release inventory first, then void payment, then done. One command in flight at a time. */
    private Command nextCompensation(Saga s) {
        s.attempts = 0;
        if (s.needsRelease) {
            transition(s, SagaState.COMPENSATING, CommandType.RELEASE_INVENTORY, "releasing inventory");
            return sendStep(s, CommandType.RELEASE_INVENTORY, true);
        }
        if (s.needsVoid) {
            transition(s, SagaState.COMPENSATING, CommandType.VOID_PAYMENT, "voiding payment");
            return sendStep(s, CommandType.VOID_PAYMENT, true);
        }
        s.nextActionAt = null;
        transition(s, SagaState.COMPENSATED, null, "all compensations done");
        return null;
    }

    /** No reply to the compensation command: resend the SAME command (same idempotency key) with backoff. */
    private Command retryCompensation(Saga s) {
        s.attempts++;
        if (s.attempts > props.maxCompensationAttempts()) {
            s.nextActionAt = null;
            repo.update(s);
            log.warn("saga {} giving up compensation step {} after {} attempts, needs manual action",
                    s.orderId, s.currentStep, props.maxCompensationAttempts());
            return null;
        }
        s.nextActionAt = Instant.now().plus(backoff(s.attempts));
        s.stepTraceParent = SagaTracing.currentTraceParent();
        repo.update(s);
        log.debug("retry #{} of {} for {}", s.attempts, s.currentStep, s.orderId);
        return command(s, s.currentStep);
    }

    private Duration backoff(int attempt) {
        return props.retryBase().multipliedBy(1L << Math.min(attempt, 10));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Every state change goes through here: custom span (attributes order.id / saga.state / saga.step /
     * idempotency.key), DB update, audit row, and the single INFO log line per milestone.
     */
    private void transition(Saga s, SagaState to, CommandType step, String detail) {
        SagaState from = s.state;
        Span span = SagaTracing.start("saga.transition " + from + "->" + to, s.orderId.toString(), to.name(),
                step == null ? null : step.name(), step == null ? null : key(s.orderId, step));
        try (Scope ignored = span.makeCurrent()) {
            s.state = to;
            s.currentStep = step;
            repo.update(s);
            repo.log(s, from, to, step, detail, s.traceId);
            log.info("saga {} {} -> {} step={} ({})", s.orderId, from, to, step, detail);
        } finally {
            span.end();
        }
    }

    /** Arm the step timeout (or retry timer) for a command about to be sent and build it. */
    private Command sendStep(Saga s, CommandType type, boolean compensation) {
        s.currentStep = type;
        Duration wait = compensation ? backoff(0) : props.stepTimeout();
        s.nextActionAt = Instant.now().plus(wait);
        s.stepTraceParent = SagaTracing.currentTraceParent(); // link target if this command is never answered
        repo.update(s);
        return command(s, type);
    }

    private Command command(Saga s, CommandType type) {
        return new Command(key(s.orderId, type), s.orderId.toString(), type, s.sku, s.qty, s.amount);
    }

    /** Idempotency key: identical on every resend of the same step. */
    static String key(UUID orderId, CommandType step) {
        return orderId + ":" + step;
    }
}
