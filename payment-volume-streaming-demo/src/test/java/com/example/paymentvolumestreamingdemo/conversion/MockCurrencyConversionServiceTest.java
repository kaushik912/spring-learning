package com.example.paymentvolumestreamingdemo.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.model.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockCurrencyConversionServiceTest {

	private MockCurrencyConversionService service;

	@BeforeEach
	void setUp() {
		service = new MockCurrencyConversionService(new PaymentsProperties());
		service.seedInitialRates();
	}

	@Test
	void givenUsdCurrency_whenConverting_thenAmountUnchanged() {
		// Given
		BigDecimal amount = BigDecimal.valueOf(100);

		// When
		BigDecimal result = service.convertToUsd(amount, Currency.USD, Instant.now());

		// Then
		assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100));
	}

	@Test
	void givenEurPayment_whenConverting_thenUsesSeededRate() {
		// Given
		BigDecimal amount = BigDecimal.valueOf(100);

		// When
		BigDecimal result = service.convertToUsd(amount, Currency.EUR, Instant.now());

		// Then
		assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(108).setScale(2));
	}

	@Test
	void givenEventTimeBeforeAnySnapshot_whenConverting_thenFallsBackToEarliestSnapshot() {
		// Given
		Instant beforeHistory = Instant.now().minus(10, ChronoUnit.DAYS);

		// When
		BigDecimal result = service.convertToUsd(BigDecimal.valueOf(100), Currency.GBP, beforeHistory);

		// Then
		assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(127).setScale(2));
	}

	@Test
	void givenRatesChangeAfterAPayment_whenConvertingThatPaymentAgain_thenPastConversionStaysUnaffected()
			throws InterruptedException {
		// Given
		BigDecimal amount = BigDecimal.valueOf(100);
		Instant paymentTime = Instant.now();
		Thread.sleep(5);
		service.publishJitteredRates();

		// When
		BigDecimal convertedAtPaymentTime = service.convertToUsd(amount, Currency.EUR, paymentTime);

		// Then
		assertThat(convertedAtPaymentTime).isEqualByComparingTo(BigDecimal.valueOf(108).setScale(2));
	}
}
