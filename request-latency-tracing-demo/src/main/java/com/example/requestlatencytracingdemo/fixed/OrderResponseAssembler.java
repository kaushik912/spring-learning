package com.example.requestlatencytracingdemo.fixed;

import io.micrometer.observation.annotation.Observed;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrderResponseAssembler {

    @Observed(name = "order.assemble", contextualName = "assemble-response")
    public Map<String, Object> assemble(String orderId, Map<String, Object> order, double price) {
        sleep(5);
        return Map.of("orderId", orderId, "item", order.get("item"), "price", price);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
