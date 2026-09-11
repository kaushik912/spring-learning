package com.example.requestlatencytracingdemo.fixed;

import io.micrometer.observation.annotation.Observed;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrderDatabaseClient {

    @Observed(name = "order.fetch", contextualName = "fetch-order-from-db")
    public Map<String, Object> fetchFromDatabase(String orderId) {
        sleep(10);
        return Map.of("orderId", orderId, "item", "widget");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
