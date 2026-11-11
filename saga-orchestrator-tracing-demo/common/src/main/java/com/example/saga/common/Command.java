package com.example.saga.common;

/**
 * Orchestrator -> participant. commandId is the idempotency key: "{orderId}:{STEP}",
 * identical on every retry of the same step.
 */
public record Command(String commandId, String orderId, CommandType type, String sku, int qty, long amount) {
}
