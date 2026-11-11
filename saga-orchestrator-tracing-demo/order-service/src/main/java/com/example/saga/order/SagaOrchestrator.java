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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The saga's entry points: HTTP, Kafka replies, timeout/retry scheduler.
 * The state machine and DB transaction live in {@link SagaEngine}; this class only
 * (1) opens a tracing span, (2) calls the engine (one transaction, returns the next command),
 * (3) sends that command AFTER commit, still inside the span so the Kafka producer span is its child.
 * Known gap (demo scope): no outbox, so a crash between commit and send loses the command; the
 * step timeout + compensation retry is what eventually heals it.
 */
@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaEngine engine;
    private final SagaRepository repo;
    private final KafkaTemplate<String, Command> kafka;

    public SagaOrchestrator(SagaEngine engine, SagaRepository repo, KafkaTemplate<String, Command> kafka) {
        this.engine = engine;
        this.repo = repo;
        this.kafka = kafka;
    }

    // ---------------------------------------------------------------- entry point 1: HTTP

    public UUID start(OrderRequest req) {
        UUID orderId = UUID.randomUUID();
        MDC.put("order_id", orderId.toString());
        // Child of the HTTP server span created by the agent. Its context is persisted by engine.begin so that
        // timeout/retry work (no incoming message) can rejoin this very trace.
        Span span = SagaTracing.start("saga.start", orderId.toString(), SagaState.STARTED.name(),
                CommandType.RESERVE_INVENTORY.name(), SagaEngine.key(orderId, CommandType.RESERVE_INVENTORY));
        try (Scope ignored = span.makeCurrent()) {
            send(engine.begin(orderId, req));
            return orderId;
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }

    // ---------------------------------------------------------------- entry point 2: Kafka replies

    public void onReply(Reply reply) {
        MDC.put("order_id", reply.orderId());
        // The agent already made the Kafka consumer span a child of the participant's producer span,
        // which is a child of our command-send span ... so this span continues the SAME trace.
        Span span = SagaTracing.start("saga.reply " + reply.type(), reply.orderId(), null, null, reply.commandId());
        try (Scope ignored = span.makeCurrent()) {
            send(engine.applyReply(reply));
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
            MDC.remove("order_id");
        }
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
                peek.currentStep == null ? null : SagaEngine.key(peek.orderId, peek.currentStep),
                peek.traceParent, peek.stepTraceParent);
        try (Scope ignored = span.makeCurrent()) {
            send(engine.handleDue(peek.orderId));
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            log.warn("due-handling failed for {}", peek.orderId, e);
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }

    // ---------------------------------------------------------------- send

    private void send(Command cmd) {
        if (cmd == null) return;
        // partition key = orderId -> per-order ordering. The agent injects traceparent into the record headers.
        kafka.send(cmd.type().topic(), cmd.orderId(), cmd);
        log.debug("sent {} key={}", cmd.type(), cmd.commandId());
    }
}
