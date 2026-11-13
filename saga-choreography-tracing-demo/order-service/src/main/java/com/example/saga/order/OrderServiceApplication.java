package com.example.saga.order;

import com.example.saga.common.SagaCommonConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SagaCommonConfig.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        start(args);
    }

    /** Also used by the integration tests. config name keeps the services' yml files apart. */
    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(OrderServiceApplication.class)
                .properties("spring.config.name=order-service")
                .run(args);
    }
}
