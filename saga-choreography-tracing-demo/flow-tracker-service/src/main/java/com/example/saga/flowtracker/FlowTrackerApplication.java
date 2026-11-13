package com.example.saga.flowtracker;

import com.example.saga.common.SagaCommonConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SagaCommonConfig.class)
public class FlowTrackerApplication {

    public static void main(String[] args) {
        start(args);
    }

    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(FlowTrackerApplication.class)
                .properties("spring.config.name=flow-tracker-service")
                .run(args);
    }
}
