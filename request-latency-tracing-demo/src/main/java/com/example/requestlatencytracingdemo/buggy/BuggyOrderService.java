package com.example.requestlatencytracingdemo.buggy;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * THE BUG: the endpoint is slow, and there is nothing here to tell you
 * which of these four steps is the reason. Spring still records an
 * automatic "http.server.requests" timer and a root trace span for the
 * whole request - so you CAN tell the endpoint is slow in aggregate - but
 * open that trace in Zipkin and it's a single flat span with no children.
 * Is it the "DB" call? The pricing call? Validation? No way to know without
 * reading the source and guessing, or bisecting with print statements.
 */
@Service
public class BuggyOrderService {

    public Map<String, Object> getOrder(String orderId) {
        validate(orderId);
        Map<String, Object> order = fetchFromDatabase(orderId);
        double price = callPricingService(orderId);
        return assembleResponse(orderId, order, price);
    }

    private void validate(String orderId) {
        sleep(5);
    }

    private Map<String, Object> fetchFromDatabase(String orderId) {
        sleep(10);
        return Map.of("orderId", orderId, "item", "widget");
    }

    private double callPricingService(String orderId) {
        // Stands in for a slow, unreliable downstream call - a network hop,
        // an overloaded pricing service, a query without an index. The
        // specifics don't matter for this demo; the point is it dominates
        // the request and nothing surfaces that fact.
        sleep(450);
        return 19.99;
    }

    private Map<String, Object> assembleResponse(String orderId, Map<String, Object> order, double price) {
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
