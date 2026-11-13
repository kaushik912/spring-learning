package com.example.saga.order;

import com.example.saga.common.Event;
import com.example.saga.common.EventTypes;
import com.example.saga.common.Outbox;
import com.example.saga.common.SagaTracing;
import com.example.saga.common.Topics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    record OrderRequest(@NotBlank String sku, @Min(1) int qty, @DecimalMin("0.01") BigDecimal amount) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Outbox outbox;

    OrderController(JdbcTemplate jdbc, TransactionTemplate tx, Outbox outbox) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.outbox = outbox;
    }

    @PostMapping("/orders")
    ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OrderRequest req) {
        String orderId = UUID.randomUUID().toString();
        MDC.put("order_id", orderId);
        // TRACING: current span here = agent's HTTP SERVER span of POST /orders. Our span nests under it, and the
        // outbox row records OUR traceparent -> everything downstream becomes part of this trace.
        Span span = SagaTracing.start("create order", orderId, EventTypes.ORDER_CREATED, null);
        try (Scope ignored = span.makeCurrent()) {
            tx.executeWithoutResult(s -> {
                jdbc.update("insert into orders(id, sku, qty, amount, status) values (?, ?, ?, ?, 'PENDING')",
                        orderId, req.sku(), req.qty(), req.amount());
                Event e = outbox.append(Topics.ORDER_EVENTS, EventTypes.ORDER_CREATED, orderId,
                        Map.of("sku", req.sku(), "qty", req.qty(), "amount", req.amount()));
                span.setAttribute("event.id", e.eventId());
            });
            log.info("order created sku={} qty={} amount={}", req.sku(), req.qty(), req.amount());
        } finally {
            span.end();
            MDC.remove("order_id");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("orderId", orderId, "status", "PENDING"));
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        var rows = jdbc.queryForList(
                "select id as \"orderId\", sku, qty, amount, status, failure_reason as \"failureReason\" from orders where id = ?",
                id);
        return rows.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(rows.get(0));
    }
}
