package com.example.paymentvolumestreamingdemo.web.dto;

import com.example.paymentvolumestreamingdemo.model.Currency;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentAcceptedResponse(String paymentId, Instant timestamp, Currency currency, BigDecimal amount) {
}
