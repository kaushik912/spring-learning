package com.example.paymentvolumestreamingdemo.simulator;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.model.Currency;
import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Stands in for real production traffic so the rolling total has something to aggregate. */
@Component
public class PaymentSimulator {

	private static final Currency[] CURRENCIES = Currency.values();

	private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
	private final PaymentsProperties properties;

	public PaymentSimulator(KafkaTemplate<String, PaymentEvent> kafkaTemplate, PaymentsProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
	}

	@Scheduled(fixedRateString = "${payments.simulator.interval}")
	void produceRandomPayment() {
		if (!properties.getSimulator().isEnabled()) {
			return;
		}
		Currency currency = CURRENCIES[ThreadLocalRandom.current().nextInt(CURRENCIES.length)];
		PaymentEvent event = new PaymentEvent(UUID.randomUUID().toString(), randomAmount(), currency, Instant.now());
		kafkaTemplate.send(PaymentTopics.TOPIC, event);
	}

	private BigDecimal randomAmount() {
		BigDecimal min = properties.getSimulator().getMinAmount();
		BigDecimal max = properties.getSimulator().getMaxAmount();
		double value = ThreadLocalRandom.current().nextDouble(min.doubleValue(), max.doubleValue());
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
	}
}
