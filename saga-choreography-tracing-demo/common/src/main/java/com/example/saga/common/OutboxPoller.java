package com.example.saga.common;

import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publishes unpublished outbox rows to Kafka, in insertion order. At-least-once: consumers are idempotent. */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private record Row(long id, String topic, String key, String eventType, String payload, String traceparent) {
    }

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxPoller(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${saga.outbox.poll-interval-ms:300}")
    void poll() {
        // TRACING: poll thread has no trace. Run under an unsampled parent so the empty polling query is not recorded.
        try (Scope ignored = SagaTracing.unsampled()) {
            List<Row> rows = jdbc.query("""
                            select id, topic, msg_key, event_type, payload, traceparent
                            from outbox where published_at is null order by id limit 50""",
                    (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6)));
            for (Row row : rows) {
                if (!publish(row)) {
                    return; // keep ordering: retry from this row on the next tick
                }
                jdbc.update("update outbox set published_at = now() where id = ?", row.id());
            }
        }
    }

    private boolean publish(Row row) {
        // TRACING: re-enter the trace of the span that wrote this row. The agent's Kafka producer instrumentation
        // creates the "publish" span as a child of it and injects `traceparent` into the Kafka record headers,
        // so the downstream consumer continues the ORIGINAL trace.
        try (Scope restored = SagaTracing.restore(row.traceparent())) {
            MDC.put("order_id", row.key());
            kafka.send(new ProducerRecord<>(row.topic(), row.key(), row.payload())).get(10, TimeUnit.SECONDS);
            log.info("event published type={} topic={}", row.eventType(), row.topic());
            return true;
        } catch (Exception e) {
            log.warn("publish failed, will retry: {}", e.toString());
            return false;
        } finally {
            MDC.remove("order_id");
        }
    }
}
