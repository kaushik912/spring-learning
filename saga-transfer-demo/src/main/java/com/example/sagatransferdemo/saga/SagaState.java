package com.example.sagatransferdemo.saga;

import java.math.BigDecimal;

/** Immutable — the orchestrator swaps the whole value atomically in its ConcurrentHashMap. */
public record SagaState(
        String sagaId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        boolean simulateCreditFailure,
        SagaStatus status,
        String reason) {

    public SagaState withStatus(SagaStatus newStatus, String newReason) {
        return new SagaState(sagaId, fromAccountId, toAccountId, amount, simulateCreditFailure, newStatus, newReason);
    }
}
