package com.example.emailkafka;

import com.example.emailkafka.EmailKafkaApplication.DemoProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic emailRequests() {
        return TopicBuilder.name(Topics.EMAIL_REQUESTS).partitions(Topics.PARTITIONS).replicas(1).build();
    }

    /** Needs at least as many partitions as the source topic (the recoverer keeps the partition number). */
    @Bean
    NewTopic emailRequestsDlt() {
        return TopicBuilder.name(Topics.EMAIL_REQUESTS_DLT).partitions(Topics.PARTITIONS).replicas(1).build();
    }

    /**
     * Retry policy for the listener: if it throws, retry the same record after a pause, `retries` times,
     * then publish it to the dead-letter topic and move on. The offset is committed only after the listener
     * returned normally (or the record was dead-lettered), so a crash mid-processing means redelivery.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> template,
            DemoProperties props) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template),
                new FixedBackOff(props.retryBackoffMs(), props.retries())));
        return factory;
    }
}
