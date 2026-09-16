package com.example.paymentvolumestreamingdemo.conversion;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.model.Currency;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Simulates a real-time FX feed: every payments.fx.jitter-interval it publishes a new,
 * immutable snapshot of rates - it never rewrites a past snapshot. convertToUsd resolves
 * the rate in effect at the payment's own timestamp via a floor lookup on the time series,
 * which is the financially-correct semantic: a past transfer is valued at the rate that
 * applied when it happened, not at today's rate.
 */
@Service
public class MockCurrencyConversionService implements CurrencyConversionService {

	private static final Logger log = LoggerFactory.getLogger(MockCurrencyConversionService.class);

	private final ConcurrentSkipListMap<Instant, Map<Currency, BigDecimal>> rateHistory = new ConcurrentSkipListMap<>();
	private final PaymentsProperties properties;

	public MockCurrencyConversionService(PaymentsProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void seedInitialRates() {
		Map<Currency, BigDecimal> baseRates = new EnumMap<>(Currency.class);
		baseRates.put(Currency.USD, BigDecimal.ONE);
		baseRates.put(Currency.EUR, BigDecimal.valueOf(1.08));
		baseRates.put(Currency.GBP, BigDecimal.valueOf(1.27));
		rateHistory.put(Instant.now(), baseRates);
	}

	@Scheduled(fixedRateString = "${payments.fx.jitter-interval}")
	void publishJitteredRates() {
		Map.Entry<Instant, Map<Currency, BigDecimal>> latest = rateHistory.lastEntry();
		if (latest == null) {
			return;
		}
		Map<Currency, BigDecimal> jittered = new EnumMap<>(Currency.class);
		latest.getValue().forEach((currency, rate) -> jittered.put(currency, jitter(currency, rate)));
		Instant now = Instant.now();
		rateHistory.put(now, jittered);
		log.debug("Published new FX snapshot at {}: {}", now, jittered);
	}

	private BigDecimal jitter(Currency currency, BigDecimal rate) {
		if (currency == Currency.USD) {
			return rate;
		}
		double magnitude = properties.getFx().getJitterMagnitudePercent() / 100.0;
		double factor = 1 + ThreadLocalRandom.current().nextDouble(-magnitude, magnitude);
		return rate.multiply(BigDecimal.valueOf(factor)).setScale(6, RoundingMode.HALF_UP);
	}

	@Override
	public BigDecimal convertToUsd(BigDecimal amount, Currency currency, Instant eventTime) {
		Map.Entry<Instant, Map<Currency, BigDecimal>> snapshot = rateHistory.floorEntry(eventTime);
		if (snapshot == null) {
			snapshot = rateHistory.firstEntry();
		}
		if (snapshot == null) {
			throw new IllegalStateException("No FX rates available yet");
		}
		BigDecimal rate = snapshot.getValue().get(currency);
		return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
	}
}
