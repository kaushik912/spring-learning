package com.example.saga.inventory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        start(args);
    }

    public static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(InventoryServiceApplication.class)
                .properties("spring.config.name=inventory-service")
                .run(args);
    }
}
