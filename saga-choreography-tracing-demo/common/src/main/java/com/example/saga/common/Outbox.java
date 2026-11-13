package com.example.saga.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transactional outbox. Callers invoke append() inside the same DB transaction as their state change, so
 * "state changed" and "event will be published" commit atomically. OutboxPoller does the actual Kafka send.
 */
@Component
public class Outbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String service;

    Outbox(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${spring.application.name}") String service) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.service = service;
    }

    public Event append(String topic, String eventType, String orderId, Map<String, Object> payload) {
        Event event = Event.of(eventType, orderId, service, payload);
        try {
            // TRACING: the poller runs on another thread, later. Persist the traceparent of the span that is
            // current right now (the HTTP / consumer handler span) so the poller can restore it when publishing.
            jdbc.update("""
                            insert into outbox(topic, msg_key, event_type, payload, traceparent)
                            values (?, ?, ?, ?, ?)""",
                    topic, orderId, eventType, mapper.writeValueAsString(event), SagaTracing.currentTraceParent());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return event;
    }
}
