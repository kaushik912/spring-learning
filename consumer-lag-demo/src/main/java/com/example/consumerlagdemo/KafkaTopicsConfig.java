package com.example.consumerlagdemo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * All three topics get the same partition count. What differs between the
 * buggy and fixed scenarios is each listener's @KafkaListener concurrency
 * (how many consumer threads actually work the partitions), not the
 * partition count itself.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final int PARTITIONS = 3;

    @Bean
    public NewTopic buggyOrders() {
        return TopicBuilder.name(OrderTopics.BUGGY_ORDERS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic fastOrders() {
        return TopicBuilder.name(OrderTopics.FAST_ORDERS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic scaledOrders() {
        return TopicBuilder.name(OrderTopics.SCALED_ORDERS).partitions(PARTITIONS).replicas(1).build();
    }
}
