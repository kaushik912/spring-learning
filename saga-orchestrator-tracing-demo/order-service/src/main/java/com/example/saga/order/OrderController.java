package com.example.saga.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {

    private final SagaOrchestrator orchestrator;
    private final SagaRepository repo;

    public OrderController(SagaOrchestrator orchestrator, SagaRepository repo) {
        this.orchestrator = orchestrator;
        this.repo = repo;
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OrderRequest req) {
        UUID orderId = orchestrator.start(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("orderId", orderId));
    }

    /** Saga state + step timeline. trace_id links straight to Jaeger (localhost:16686/trace/{traceId}). */
    @GetMapping("/orders/{orderId}/saga")
    public ResponseEntity<Map<String, Object>> saga(@PathVariable UUID orderId) {
        return repo.findByOrderId(orderId).map(s -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderId", s.orderId);
            body.put("state", s.state);
            body.put("currentStep", s.currentStep);
            body.put("traceId", s.traceId);
            body.put("timeline", repo.timeline(s.id));
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
