package com.example.duplicatedeliverydemo;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic buggyEvents() {
        return TopicBuilder.name(EventTopics.BUGGY_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic fixedEvents() {
        return TopicBuilder.name(EventTopics.FIXED_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic persistentEvents() {
        return TopicBuilder.name(EventTopics.PERSISTENT_EVENTS).partitions(1).replicas(1).build();
    }
}
