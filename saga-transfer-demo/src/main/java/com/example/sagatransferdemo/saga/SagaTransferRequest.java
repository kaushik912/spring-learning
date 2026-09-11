package com.example.sagatransferdemo.saga;

import java.math.BigDecimal;

public record SagaTransferRequest(
        String fromAccountId, String toAccountId, BigDecimal amount, boolean simulateCreditFailure) {
}
