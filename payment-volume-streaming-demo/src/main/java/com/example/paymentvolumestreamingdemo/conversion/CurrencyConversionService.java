package com.example.paymentvolumestreamingdemo.conversion;

import com.example.paymentvolumestreamingdemo.model.Currency;
import java.math.BigDecimal;
import java.time.Instant;

public interface CurrencyConversionService {

	/** Converts using the FX rate that was in effect at {@code eventTime}, not "now". */
	BigDecimal convertToUsd(BigDecimal amount, Currency currency, Instant eventTime);
}
