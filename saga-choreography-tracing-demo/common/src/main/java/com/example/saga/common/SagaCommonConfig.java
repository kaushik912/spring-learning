package com.example.saga.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.ArrayList;
import java.util.List;

/** Imported by every service: scans this package, creates topics, installs retry + DLT error handling. */
@Configuration
@ComponentScan
@EnableScheduling
public class SagaCommonConfig {

    private static final int PARTITIONS = 3;

    /** Every service declares every topic (idempotent) so start-up order does not matter. */
    @Bean
    KafkaAdmin.NewTopics sagaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String t : List.of(Topics.ORDER_EVENTS, Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS)) {
            topics.add(TopicBuilder.name(t).partitions(PARTITIONS).replicas(1).build());
            topics.add(TopicBuilder.name(t + ".DLT").partitions(PARTITIONS).replicas(1).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(new NewTopic[0]));
    }

    /** Retry 3x with exponential backoff (0.5s, 1s, 2s), then publish to <topic>.DLT. Poison messages skip retries. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(3);
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
