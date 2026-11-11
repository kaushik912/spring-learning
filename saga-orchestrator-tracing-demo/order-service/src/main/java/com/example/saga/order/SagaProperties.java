package com.example.saga.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "saga")
public record SagaProperties(Duration stepTimeout, Duration retryBase, int maxCompensationAttempts) {
}
