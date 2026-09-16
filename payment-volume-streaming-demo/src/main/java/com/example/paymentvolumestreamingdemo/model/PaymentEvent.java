package com.example.paymentvolumestreamingdemo.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(String id, BigDecimal amount, Currency currency, Instant timestamp) {
}
