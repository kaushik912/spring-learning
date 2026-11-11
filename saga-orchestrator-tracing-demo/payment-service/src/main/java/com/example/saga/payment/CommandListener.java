package com.example.saga.payment;

import com.example.saga.common.Command;
import com.example.saga.common.Reply;
import com.example.saga.common.SagaTracing;
import com.example.saga.common.Topics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandListener {

    private final PaymentService service;
    private final KafkaTemplate<String, Reply> kafka;

    public CommandListener(PaymentService service, KafkaTemplate<String, Reply> kafka) {
        this.service = service;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS)
    void onCommand(Command c) {
        MDC.put("order_id", c.orderId());
        Span span = SagaTracing.start("payment.handle " + c.type(), c.orderId(), null, c.type().name(), c.commandId());
        try (Scope ignored = span.makeCurrent()) {
            Reply reply = service.handle(c);
            kafka.send(Topics.ORDER_REPLIES, c.orderId(), reply);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
            MDC.remove("order_id");
        }
    }
}
