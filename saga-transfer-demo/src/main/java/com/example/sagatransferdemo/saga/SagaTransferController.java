package com.example.sagatransferdemo.saga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed - Saga", description = "Transfer as a sequence of local transactions with compensation on failure")
public class SagaTransferController {

    private final SagaOrchestrator sagaOrchestrator;

    public SagaTransferController(SagaOrchestrator sagaOrchestrator) {
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Operation(summary = "Start a transfer as a saga (fixed: each step is local, failures are compensated)")
    @PostMapping("/api/saga/transfers")
    public ResponseEntity<Map<String, String>> startTransfer(@RequestBody SagaTransferRequest request) {
        String sagaId = sagaOrchestrator.start(
                request.fromAccountId(), request.toAccountId(), request.amount(), request.simulateCreditFailure());
        return ResponseEntity.accepted().body(Map.of("sagaId", sagaId));
    }

    @Operation(summary = "Check saga status")
    @GetMapping("/api/saga/transfers/{sagaId}")
    public ResponseEntity<SagaState> getStatus(@PathVariable String sagaId) {
        SagaState state = sagaOrchestrator.getState(sagaId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }
}
