package com.example.sagatransferdemo.saga;

import java.math.BigDecimal;

/**
 * The credit-requested command. simulateFailure is a deliberate fault-injection
 * hook for this demo only — a real receiver-service wouldn't take instructions
 * from the caller on whether to fail; it's here so tests can deterministically
 * exercise the compensation path.
 */
public record CreditCommand(String sagaId, String accountId, BigDecimal amount, boolean simulateFailure) {
}
