package com.example.saga.order;

import com.example.saga.common.Event;
import com.example.saga.common.EventProcessor;
import com.example.saga.common.EventTypes;
import com.example.saga.common.Outbox;
import com.example.saga.common.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Order-service reacts to the other services' events: it is the end of the happy path and of both failure paths. */
@Component
class OrderEventListener {

    private final EventProcessor processor;
    private final JdbcTemplate jdbc;
    private final Outbox outbox;
    private final Map<String, Consumer<Event>> handlers = Map.of(
            EventTypes.INVENTORY_REJECTED, e -> finish(e, "REJECTED", EventTypes.ORDER_REJECTED),
            EventTypes.PAYMENT_FAILED, e -> finish(e, "CANCELLED", EventTypes.ORDER_CANCELLED),
            EventTypes.PAYMENT_AUTHORIZED, e -> finish(e, "CONFIRMED", EventTypes.ORDER_CONFIRMED));

    OrderEventListener(EventProcessor processor, JdbcTemplate jdbc, Outbox outbox) {
        this.processor = processor;
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    void on(ConsumerRecord<String, String> record) {
        processor.process(record, handlers);
    }

    private void finish(Event in, String status, String outEvent) {
        // only PENDING orders move; a late/out-of-order event can never overwrite a final status
        int updated = jdbc.update("""
                update orders set status = ?, failure_reason = ?, updated_at = now()
                where id = ? and status = 'PENDING'""", status, in.text("reason"), in.orderId());
        if (updated == 1) {
            outbox.append(Topics.ORDER_EVENTS, outEvent, in.orderId(), Map.of("status", status));
        }
    }
}
