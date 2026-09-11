package com.example.sagatransferdemo.saga;

/** The outcome of one saga step, published back to the orchestrator. */
public record StepResult(String sagaId, boolean success, String reason) {
}
