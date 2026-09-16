package com.example.paymentvolumestreamingdemo.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Single partition is intentional: every event is re-keyed to one constant
 * aggregate key (see PaymentTopics.AGGREGATE_KEY), so extra partitions would
 * never be used for parallelism anyway - the whole point is one running total.
 */
@Configuration
public class KafkaTopicsConfig {

	@Bean
	public NewTopic paymentEvents() {
		return TopicBuilder.name(PaymentTopics.TOPIC).partitions(1).replicas(1).build();
	}
}
