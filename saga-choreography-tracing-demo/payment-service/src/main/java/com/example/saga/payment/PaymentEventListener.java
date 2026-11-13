package com.example.saga.payment;

import com.example.saga.common.Event;
import com.example.saga.common.EventProcessor;
import com.example.saga.common.EventTypes;
import com.example.saga.common.Outbox;
import com.example.saga.common.SagaTracing;
import com.example.saga.common.Topics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

@Component
class PaymentEventListener {

    private final EventProcessor processor;
    private final JdbcTemplate jdbc;
    private final Outbox outbox;
    private final Duration slowGatewayDelay;
    private final Map<String, Consumer<Event>> handlers = Map.of(EventTypes.INVENTORY_RESERVED, this::authorize);

    PaymentEventListener(EventProcessor processor, JdbcTemplate jdbc, Outbox outbox,
                         @Value("${payment.slow-gateway-delay:10s}") Duration slowGatewayDelay) {
        this.processor = processor;
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.slowGatewayDelay = slowGatewayDelay;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS)
    void on(ConsumerRecord<String, String> record) {
        processor.process(record, handlers);
    }

    private void authorize(Event in) {
        BigDecimal amount = in.decimal("amount");
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            jdbc.update("insert into payment(order_id, amount, status) values (?, ?, 'FAILED')", in.orderId(), amount);
            outbox.append(Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED, in.orderId(),
                    Map.of("reason", "AMOUNT_LIMIT_EXCEEDED"));
            return;
        }
        if (amount.compareTo(new BigDecimal("777")) == 0) {
            callSlowGateway(in.orderId());
        }
        jdbc.update("insert into payment(order_id, amount, status) values (?, ?, 'AUTHORIZED')", in.orderId(), amount);
        outbox.append(Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_AUTHORIZED, in.orderId(), Map.of("amount", amount));
    }

    /** Own span so the 10s gap is a visible bar in the Jaeger waterfall. */
    private void callSlowGateway(String orderId) {
        Span gateway = SagaTracing.start("payment-gateway slow call", orderId, null, null);
        try (Scope ignored = gateway.makeCurrent()) {
            Thread.sleep(slowGatewayDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            gateway.end();
        }
    }
}
