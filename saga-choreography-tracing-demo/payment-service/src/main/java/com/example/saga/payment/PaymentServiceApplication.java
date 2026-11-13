package com.example.saga.payment;

import com.example.saga.common.SagaCommonConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SagaCommonConfig.class)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        start(args);
    }

    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(PaymentServiceApplication.class)
                .properties("spring.config.name=payment-service")
                .run(args);
    }
}
