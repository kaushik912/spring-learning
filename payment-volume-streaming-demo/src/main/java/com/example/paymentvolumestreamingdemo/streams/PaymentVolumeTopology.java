package com.example.paymentvolumestreamingdemo.streams;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.conversion.CurrencyConversionService;
import com.example.paymentvolumestreamingdemo.kafka.BigDecimalSerde;
import com.example.paymentvolumestreamingdemo.kafka.PaymentEventTimestampExtractor;
import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import java.math.BigDecimal;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.streams.KafkaStreamsInteractiveQueryService;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Hopping windows (24h size, 1h advance by default) rather than SlidingWindows: hopping
 * windows sit on a fixed grid, which is the standard, well-documented shape for Interactive
 * Query range scans over a WindowStore. The cost is that "the last 24h" is only accurate to
 * within one advance interval of staleness - see README "Discussion" section for the
 * SlidingWindows alternative and why it isn't used here.
 */
@Configuration
@EnableKafkaStreams
public class PaymentVolumeTopology {

	@Bean
	public KStream<String, PaymentEvent> paymentVolumeStream(
			StreamsBuilder streamsBuilder, CurrencyConversionService conversionService, PaymentsProperties properties) {
		return buildTopology(streamsBuilder, conversionService, properties);
	}

	public static KStream<String, PaymentEvent> buildTopology(
			StreamsBuilder streamsBuilder, CurrencyConversionService conversionService, PaymentsProperties properties) {

		JsonSerde<PaymentEvent> paymentEventSerde = new JsonSerde<>(PaymentEvent.class).ignoreTypeHeaders();

		KStream<String, PaymentEvent> stream = streamsBuilder.stream(
				PaymentTopics.TOPIC,
				Consumed.with(Serdes.String(), paymentEventSerde)
						.withTimestampExtractor(new PaymentEventTimestampExtractor()));

		TimeWindows windows = TimeWindows
				.ofSizeAndGrace(properties.getWindow().getSize(), properties.getWindow().getGrace())
				.advanceBy(properties.getWindow().getAdvance());

		stream
				.selectKey((key, event) -> PaymentTopics.AGGREGATE_KEY)
				.mapValues(event -> conversionService.convertToUsd(event.amount(), event.currency(), event.timestamp()))
				.groupByKey(Grouped.with(Serdes.String(), BigDecimalSerde.instance()))
				.windowedBy(windows)
				.aggregate(
						() -> BigDecimal.ZERO,
						(key, usdAmount, total) -> total.add(usdAmount),
						Materialized.<String, BigDecimal, WindowStore<Bytes, byte[]>>as(PaymentTopics.STORE_NAME)
								.withKeySerde(Serdes.String())
								.withValueSerde(BigDecimalSerde.instance())
								.withRetention(properties.getWindow().getSize().plus(properties.getWindow().getGrace())));

		return stream;
	}

	@Bean
	public KafkaStreamsInteractiveQueryService interactiveQueryService(StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
		return new KafkaStreamsInteractiveQueryService(streamsBuilderFactoryBean);
	}
}
