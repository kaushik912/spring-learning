package com.example.paymentvolumestreamingdemo.kafka;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

/** Kafka Streams has no built-in BigDecimal serde, and money must not be a double. */
public final class BigDecimalSerde {

	private BigDecimalSerde() {
	}

	public static Serde<BigDecimal> instance() {
		Serializer<BigDecimal> serializer =
				(topic, data) -> data == null ? null : data.toPlainString().getBytes(StandardCharsets.UTF_8);
		Deserializer<BigDecimal> deserializer =
				(topic, data) -> data == null ? null : new BigDecimal(new String(data, StandardCharsets.UTF_8));
		return Serdes.serdeFrom(serializer, deserializer);
	}
}
