package com.example.paymentvolumestreamingdemo.web.dto;

import com.example.paymentvolumestreamingdemo.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record SubmitPaymentRequest(
		@NotNull @Positive BigDecimal amount,
		@NotNull Currency currency,
		Instant timestamp) {
}
