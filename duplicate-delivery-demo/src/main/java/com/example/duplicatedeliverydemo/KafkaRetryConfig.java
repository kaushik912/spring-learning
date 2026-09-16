package com.example.duplicatedeliverydemo;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Stands in for the real-world reason Kafka redelivers a record to the same
 * consumer group: the listener processes it, something after the side
 * effect fails (a downstream call, an audit write, the offset commit
 * itself), and Spring Kafka's error handler seeks the record back so the
 * container hands it to the listener again. One retry (FixedBackOff with
 * maxAttempts = 1, i.e. the original delivery plus exactly one redelivery)
 * keeps the reproduction deterministic instead of relying on a real
 * consumer crash/restart or group rebalance — which trigger redelivery the
 * same way, just not on demand in a test.
 */
@Configuration
public class KafkaRetryConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 1L)));
        return factory;
    }
}
