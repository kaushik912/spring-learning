package com.example.sagatransferdemo.buggy;

import java.math.BigDecimal;

public record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount, boolean simulateCrash) {
}
