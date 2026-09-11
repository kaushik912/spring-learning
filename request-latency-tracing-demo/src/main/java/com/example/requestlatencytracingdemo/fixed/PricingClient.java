package com.example.requestlatencytracingdemo.fixed;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Component;

@Component
public class PricingClient {

    @Observed(name = "order.pricing", contextualName = "call-pricing-service")
    public double getPrice(String orderId) {
        // Same slow, unreliable-downstream-call stand-in as the buggy
        // version - identical latency, the only difference is visibility.
        sleep(450);
        return 19.99;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
