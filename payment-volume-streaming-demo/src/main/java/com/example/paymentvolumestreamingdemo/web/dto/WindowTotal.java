package com.example.paymentvolumestreamingdemo.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WindowTotal(Instant windowStart, Instant windowEnd, BigDecimal totalUsd, String status) {

	public static WindowTotal noData() {
		return new WindowTotal(null, null, BigDecimal.ZERO, "NO_DATA");
	}
}
