package com.example.emaildedupdemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
        String instanceId,
        int userCount,
        int workers,
        int batchSize,
        long providerLatencyMs,
        double providerFailureRate,
        double crashAfterSendRate,
        long claimTimeoutMs,
        long reaperIntervalMs) {
}
