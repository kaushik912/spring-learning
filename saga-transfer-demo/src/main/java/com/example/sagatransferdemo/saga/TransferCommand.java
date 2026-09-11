package com.example.sagatransferdemo.saga;

import java.math.BigDecimal;

/** Used for debit-requested and refund-requested — a plain "move money on this one account" command. */
public record TransferCommand(String sagaId, String accountId, BigDecimal amount) {
}
