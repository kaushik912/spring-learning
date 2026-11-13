package com.example.saga.inventory;

import com.example.saga.common.Event;
import com.example.saga.common.EventProcessor;
import com.example.saga.common.EventTypes;
import com.example.saga.common.Outbox;
import com.example.saga.common.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
class InventoryEventListener {

    private final EventProcessor processor;
    private final JdbcTemplate jdbc;
    private final Outbox outbox;
    private final Map<String, Consumer<Event>> handlers = Map.of(
            EventTypes.ORDER_CREATED, this::reserve,
            EventTypes.PAYMENT_FAILED, this::release);   // compensation

    InventoryEventListener(EventProcessor processor, JdbcTemplate jdbc, Outbox outbox) {
        this.processor = processor;
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS})
    void on(ConsumerRecord<String, String> record) {
        processor.process(record, handlers);
    }

    private void reserve(Event in) {
        String sku = in.text("sku");
        int qty = in.integer("qty");
        // atomic conditional decrement: no read-then-write race
        int updated = jdbc.update("update stock set qty = qty - ? where sku = ? and qty >= ?", qty, sku, qty);
        if (updated == 1) {
            jdbc.update("insert into reservation(order_id, sku, qty, status) values (?, ?, ?, 'RESERVED')",
                    in.orderId(), sku, qty);
            // event-carried state: payment-service needs the amount but never calls order-service
            outbox.append(Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_RESERVED, in.orderId(),
                    Map.of("sku", sku, "qty", qty, "amount", in.decimal("amount")));
        } else {
            outbox.append(Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_REJECTED, in.orderId(),
                    Map.of("reason", "OUT_OF_STOCK"));
        }
    }

    private void release(Event in) {
        List<Map<String, Object>> reserved = jdbc.queryForList(
                "select sku, qty from reservation where order_id = ? and status = 'RESERVED'", in.orderId());
        if (reserved.isEmpty()) {
            return; // nothing reserved (or already released)
        }
        String sku = (String) reserved.get(0).get("sku");
        int qty = (Integer) reserved.get(0).get("qty");
        jdbc.update("update stock set qty = qty + ? where sku = ?", qty, sku);
        jdbc.update("update reservation set status = 'RELEASED' where order_id = ?", in.orderId());
        outbox.append(Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_RELEASED, in.orderId(),
                Map.of("sku", sku, "qty", qty));
    }
}
