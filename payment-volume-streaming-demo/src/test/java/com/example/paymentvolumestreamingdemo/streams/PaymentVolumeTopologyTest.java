package com.example.paymentvolumestreamingdemo.streams;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.conversion.CurrencyConversionService;
import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.model.Currency;
import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Exercises the Kafka Streams DSL topology directly (windowing, FX conversion wiring)
 * via TopologyTestDriver - no broker needed. Window size/advance/grace are shrunk to
 * seconds here purely to keep test timestamps readable; production values live in
 * application.yml.
 */
class PaymentVolumeTopologyTest {

	private TopologyTestDriver testDriver;
	private TestInputTopic<String, PaymentEvent> inputTopic;
	private PaymentsProperties properties;

	@BeforeEach
	void setUp() {
		properties = new PaymentsProperties();
		properties.getWindow().setSize(Duration.ofHours(2));
		properties.getWindow().setAdvance(Duration.ofHours(1));
		properties.getWindow().setGrace(Duration.ofMinutes(5));

		CurrencyConversionService fixedRateConversion = (amount, currency, eventTime) -> switch (currency) {
			case USD -> amount;
			case EUR -> amount.multiply(BigDecimal.valueOf(1.08));
			case GBP -> amount.multiply(BigDecimal.valueOf(1.27));
		};

		StreamsBuilder streamsBuilder = new StreamsBuilder();
		PaymentVolumeTopology.buildTopology(streamsBuilder, fixedRateConversion, properties);
		Topology topology = streamsBuilder.build();

		Properties config = new Properties();
		config.put(StreamsConfig.APPLICATION_ID_CONFIG, "payment-volume-topology-test");
		config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

		testDriver = new TopologyTestDriver(topology, config);
		inputTopic = testDriver.createInputTopic(
				PaymentTopics.TOPIC,
				Serdes.String().serializer(),
				new JsonSerde<>(PaymentEvent.class).ignoreTypeHeaders().serializer());
	}

	@AfterEach
	void tearDown() {
		testDriver.close();
	}

	@Test
	void givenPaymentsInMultipleCurrencies_whenAggregated_thenTotalReflectsUsdConvertedSum() {
		// Given
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		publish("p1", BigDecimal.valueOf(100), Currency.USD, base);
		publish("p2", BigDecimal.valueOf(100), Currency.EUR, base.plusSeconds(10));
		publish("p3", BigDecimal.valueOf(100), Currency.GBP, base.plusSeconds(20));

		// When
		BigDecimal total = windowTotalStartingAt(base);

		// Then
		assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(335)); // 100 + 108 + 127
	}

	@Test
	void givenLatePaymentWithinGracePeriod_whenProcessed_thenIncludedInWindowTotal() {
		// Given
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		publish("p1", BigDecimal.valueOf(50), Currency.USD, base);
		// Advance stream time just past window end, but still within the grace period.
		Instant justPastWindowEnd = base.plus(properties.getWindow().getSize()).plusSeconds(1);
		publish("advance", BigDecimal.ZERO, Currency.USD, justPastWindowEnd);
		publish("late", BigDecimal.valueOf(25), Currency.USD, base.plusSeconds(30));

		// When
		BigDecimal total = windowTotalStartingAt(base);

		// Then
		assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(75));
	}

	@Test
	void givenPaymentPastGracePeriod_whenProcessed_thenExcludedFromClosedWindow() {
		// Given
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		publish("p1", BigDecimal.valueOf(50), Currency.USD, base);
		// Advance stream time past window end + grace, closing the window for good.
		Instant pastGrace = base.plus(properties.getWindow().getSize()).plus(properties.getWindow().getGrace()).plusSeconds(1);
		publish("advance", BigDecimal.ZERO, Currency.USD, pastGrace);
		publish("tooLate", BigDecimal.valueOf(999), Currency.USD, base.plusSeconds(30));

		// When
		BigDecimal total = windowTotalStartingAt(base);

		// Then
		assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(50));
	}

	private void publish(String id, BigDecimal amount, Currency currency, Instant timestamp) {
		PaymentEvent event = new PaymentEvent(id, amount, currency, timestamp);
		inputTopic.pipeInput(PaymentTopics.AGGREGATE_KEY, event, timestamp);
	}

	private BigDecimal windowTotalStartingAt(Instant windowStart) {
		WindowStore<String, BigDecimal> store = testDriver.getWindowStore(PaymentTopics.STORE_NAME);
		try (WindowStoreIterator<BigDecimal> iterator =
				store.fetch(PaymentTopics.AGGREGATE_KEY, windowStart, windowStart.plusSeconds(1))) {
			BigDecimal latest = BigDecimal.ZERO;
			while (iterator.hasNext()) {
				KeyValue<Long, BigDecimal> next = iterator.next();
				if (next.key == windowStart.toEpochMilli()) {
					latest = next.value;
				}
			}
			return latest;
		}
	}
}
