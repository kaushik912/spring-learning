package com.example.sagatransferdemo.saga;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic debitRequested() {
        return TopicBuilder.name(SagaTopics.DEBIT_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic debitResult() {
        return TopicBuilder.name(SagaTopics.DEBIT_RESULT).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic creditRequested() {
        return TopicBuilder.name(SagaTopics.CREDIT_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic creditResult() {
        return TopicBuilder.name(SagaTopics.CREDIT_RESULT).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic refundRequested() {
        return TopicBuilder.name(SagaTopics.REFUND_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic refundResult() {
        return TopicBuilder.name(SagaTopics.REFUND_RESULT).partitions(1).replicas(1).build();
    }
}
