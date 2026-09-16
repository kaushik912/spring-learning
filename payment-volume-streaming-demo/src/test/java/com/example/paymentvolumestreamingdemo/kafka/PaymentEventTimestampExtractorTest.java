package com.example.paymentvolumestreamingdemo.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentvolumestreamingdemo.model.Currency;
import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentEventTimestampExtractorTest {

	private final PaymentEventTimestampExtractor extractor = new PaymentEventTimestampExtractor();

	@Test
	void givenPaymentEventRecord_whenExtractingTimestamp_thenReturnsEventTimestampNotPartitionTime() {
		// Given
		Instant eventTime = Instant.parse("2026-01-01T00:00:00Z");
		PaymentEvent event = new PaymentEvent("id-1", BigDecimal.TEN, Currency.USD, eventTime);
		ConsumerRecord<Object, Object> record = new ConsumerRecord<>(PaymentTopics.TOPIC, 0, 0L, "ALL", event);
		long partitionTime = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();

		// When
		long extracted = extractor.extract(record, partitionTime);

		// Then
		assertThat(extracted).isEqualTo(eventTime.toEpochMilli());
	}
}
