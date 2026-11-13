package com.example.saga.common;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event envelope. orderId doubles as correlation id and Kafka message key. */
public record Event(String eventId, String eventType, String orderId, Instant occurredAt, String producer,
                    Map<String, Object> payload) {

    public Event {
        payload = payload == null ? Map.of() : payload;
    }

    public static Event of(String eventType, String orderId, String producer, Map<String, Object> payload) {
        return new Event(UUID.randomUUID().toString(), eventType, orderId, Instant.now(), producer, payload);
    }

    public String text(String key) {
        Object v = payload.get(key);
        return v == null ? null : v.toString();
    }

    public int integer(String key) {
        return ((Number) payload.get(key)).intValue();
    }

    public BigDecimal decimal(String key) {
        return new BigDecimal(payload.get(key).toString());
    }
}
