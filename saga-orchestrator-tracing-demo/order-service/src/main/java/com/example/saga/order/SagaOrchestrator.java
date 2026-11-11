package com.example.saga.order;

import com.example.saga.common.Command;
import com.example.saga.common.CommandType;
import com.example.saga.common.Reply;
import com.example.saga.common.SagaTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The saga state machine.
 * <pre>
 * STARTED --InventoryReserved--> INVENTORY_RESERVED --PaymentAuthorized--> PAYMENT_AUTHORIZED -> COMPLETED
 *    |                                  |
 *    | InventoryRejected                | PaymentFailed / timeout
 *    v                                  v
 * COMPENSATING --(ReleaseInventory, then VoidPayment if owed; retried w/ backoff)--> COMPENSATED
 * </pre>
 * Pattern for every entry point: open a tracing span -> DB transaction (state + audit row, row-locked)
 * -> send the next command AFTER commit, still inside the span so the Kafka producer span is its child.
 * Known gap (demo scope): no outbox, so a crash between commit and send loses the command; the
 * step timeout + compensation retry is what eventually heals it.
 */
@Service
@EnableConfigurationProperties(SagaProperties.class)
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaRepository repo;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, Command> kafka;
    private final SagaProperties props;

    public SagaOrchestrator(SagaRepository repo, TransactionTemplate tx, KafkaTemplate<String, Command> kafka,
                            SagaProperties props) {
        this.repo = repo;
        this.tx = tx;
        this.kafka = kafka;
        this.props = props;
    }

    // ---------------------------------------------------------------- entry point 1: HTTP

    public UUID start(OrderRequest req) {
        UUID orderId = UUID.randomUUID();
        MDC.put("order_id", orderId.toString());
        // Child of the HTTP server span created by the agent. Its context is persisted below so that
        // timeout/retry work (no incoming message) can rejoin this very trace.
        Span span = SagaTracing.start("saga.start", orderId.toString(), SagaState.STARTED.name(),
                CommandType.RESERVE_INVENTORY.name(), key(orderId, CommandType.RESERVE_INVENTORY));
        try (Scope ignored = span.makeCurrent()) {
            Command cmd = tx.execute(st -> {
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
            });
            send(cmd);
            return orderId;
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }

    // ---------------------------------------------------------------- entry point 2: Kafka replies

    public void onReply(Reply reply) {
        UUID orderId = UUID.fromString(reply.orderId());
        MDC.put("order_id", reply.orderId());
        // The agent already made the Kafka consumer span a child of the participant's producer span,
        // which is a child of our command-send span ... so this span continues the SAME trace.
        Span span = SagaTracing.start("saga.reply " + reply.type(), reply.orderId(), null, null, reply.commandId());
        try (Scope ignored = span.makeCurrent()) {
            Command next = tx.execute(st -> applyReply(orderId, reply));
            send(next);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }

    private Command applyReply(UUID orderId, Reply r) {
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

    // ---------------------------------------------------------------- entry point 3: timeouts + retries

    @Scheduled(fixedDelayString = "${saga.scan-interval:1s}")
    void scanDue() {
        List<Saga> due;
        try (Scope ignored = SagaTracing.unsampled()) { // keep the polling query out of Jaeger
            due = repo.findDue();
        }
        due.forEach(this::handleDue);
    }

    private void handleDue(Saga peek) {
        MDC.put("order_id", peek.orderId.toString());
        boolean retry = peek.state == SagaState.COMPENSATING;
        // No incoming message here, so no natural parent. Parent = trace saved at saga start (stay in the
        // original trace), LINK = the span that sent the command nobody answered (causal, not hierarchical).
        Span span = SagaTracing.startDetached(retry ? "saga.compensation.retry" : "saga.timeout",
                peek.orderId.toString(), peek.state.name(),
                peek.currentStep == null ? null : peek.currentStep.name(),
                peek.currentStep == null ? null : key(peek.orderId, peek.currentStep),
                peek.traceParent, peek.stepTraceParent);
        try (Scope ignored = span.makeCurrent()) {
            Command next = tx.execute(st -> {
                Saga s = repo.lockByOrderId(peek.orderId).orElseThrow();
                if (s.nextActionAt == null || s.nextActionAt.isAfter(Instant.now())) {
                    return null; // handled meanwhile by a reply
                }
                return switch (s.state) {
                    case STARTED -> compensate(s, true, false, "timeout waiting for inventory");
                    case INVENTORY_RESERVED -> compensate(s, true, true, "timeout waiting for payment");
                    case COMPENSATING -> retryCompensation(s);
                    default -> null;
                };
            });
            send(next);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            log.warn("due-handling failed for {}", peek.orderId, e);
        } finally {
            span.end();
            MDC.remove("order_id");
        }
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

    private static String key(UUID orderId, CommandType step) {
        return orderId + ":" + step;
    }

    private void send(Command cmd) {
        if (cmd == null) return;
        // partition key = orderId -> per-order ordering. The agent injects traceparent into the record headers.
        kafka.send(cmd.type().topic(), cmd.orderId(), cmd);
        log.debug("sent {} key={}", cmd.type(), cmd.commandId());
    }
}
