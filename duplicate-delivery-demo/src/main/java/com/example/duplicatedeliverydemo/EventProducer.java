package com.example.duplicatedeliverydemo;

import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The eventId is both the Kafka record key and the payload — it's the
 * natural dedup key a consumer needs to recognize "I've already handled
 * this exact event," same as an idempotency key on a payment request.
 */
@Component
public class EventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public String publish(String topic) {
        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(topic, eventId, eventId);
        return eventId;
    }
}
