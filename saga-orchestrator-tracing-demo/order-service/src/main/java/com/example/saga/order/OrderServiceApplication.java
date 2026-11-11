package com.example.saga.order;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.saga.common.Topics;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        start(args);
    }

    /** Also used by the integration tests. config name keeps the three services' yml files apart. */
    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(OrderServiceApplication.class)
                .properties("spring.config.name=order-service")
                .run(args);
    }

    // order-service owns topic creation for the whole demo
    @Bean
    NewTopic inventoryCommands() {
        return TopicBuilder.name(Topics.INVENTORY_COMMANDS).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentCommands() {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic orderReplies() {
        return TopicBuilder.name(Topics.ORDER_REPLIES).partitions(1).replicas(1).build();
    }
}
