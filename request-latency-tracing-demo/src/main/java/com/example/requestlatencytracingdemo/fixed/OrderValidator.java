package com.example.requestlatencytracingdemo.fixed;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Component;

@Component
public class OrderValidator {

    @Observed(name = "order.validate", contextualName = "validate-order")
    public void validate(String orderId) {
        sleep(5);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
