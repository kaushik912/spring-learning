package com.example.saga.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Single entry point for every Kafka listener: parse -> custom span -> ONE transaction containing
 * (idempotency check + business handler + outbox append) -> metric + log.
 */
@Component
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    private final String service;

    EventProcessor(ObjectMapper mapper, JdbcTemplate jdbc, TransactionTemplate tx, MeterRegistry metrics,
                   @Value("${spring.application.name}") String service) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.tx = tx;
        this.metrics = metrics;
        this.service = service;
    }

    public void process(ConsumerRecord<String, String> record, Map<String, Consumer<Event>> handlers) {
        Event event = parse(record.value());

        Consumer<Event> handler = handlers.get(event.eventType());
        if (handler == null) {
            count(event.eventType(), "ignored");
            return;
        }

        MDC.put("order_id", event.orderId());
        // TRACING: the agent already made the Kafka "process" span current, and its parent is the producer span
        // (traceparent header) -> same trace_id as the original POST. Our span nests under it and carries the
        // business attributes; JDBC spans from the handler nest under ours.
        Span span = SagaTracing.start("handle " + event.eventType(), event.orderId(), event.eventType(),
                event.eventId());
        try (Scope ignored = span.makeCurrent()) {
            boolean firstDelivery = Boolean.TRUE.equals(tx.execute(status -> {
                // IDEMPOTENCY: PK insert on processed_event. Same eventId a 2nd time -> 0 rows -> skip handler.
                // Same transaction as the handler, so "processed" and "effects" commit or roll back together.
                int inserted = jdbc.update(
                        "insert into processed_event(event_id) values (?) on conflict do nothing", event.eventId());
                if (inserted == 0) {
                    return false;
                }
                handler.accept(event);
                return true;
            }));
            span.setAttribute("event.duplicate", !firstDelivery);
            count(event.eventType(), firstDelivery ? "processed" : "duplicate");
            log.info(firstDelivery ? "event handled type={}" : "duplicate event skipped type={}", event.eventType());
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            count(event.eventType(), "error");
            log.warn("event handling failed type={}: {}", event.eventType(), e.toString());
            throw e; // error handler: retry with backoff, then <topic>.DLT
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }

    private Event parse(String json) {
        try {
            Event e = mapper.readValue(json, Event.class);
            if (e.eventId() == null || e.eventType() == null || e.orderId() == null) {
                throw new IllegalArgumentException("envelope missing eventId/eventType/orderId");
            }
            return e;
        } catch (Exception ex) {
            count("unknown", "error");
            // IllegalArgumentException is configured as non-retryable -> straight to DLT
            throw new IllegalArgumentException("poison message: " + ex.getMessage(), ex);
        }
    }

    private void count(String eventType, String outcome) {
        metrics.counter("events.handled", "service", service, "event_type", eventType, "outcome", outcome)
                .increment();
    }
}
