package com.example.paymentvolumestreamingdemo.kafka;

import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Forces windowing to key off the payment's own event time rather than Kafka
 * ingestion/partition time - required so a backdated or replayed payment
 * lands in the window it actually belongs to.
 */
public class PaymentEventTimestampExtractor implements TimestampExtractor {

	@Override
	public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
		if (record.value() instanceof PaymentEvent paymentEvent && paymentEvent.timestamp() != null) {
			return paymentEvent.timestamp().toEpochMilli();
		}
		return partitionTime;
	}
}
