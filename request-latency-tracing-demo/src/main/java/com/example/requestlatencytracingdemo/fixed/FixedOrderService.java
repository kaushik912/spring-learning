package com.example.requestlatencytracingdemo.fixed;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * FIX: identical steps and identical timings to BuggyOrderService - the
 * only difference is each step lives in its own @Observed-annotated
 * collaborator bean (Spring AOP only intercepts calls that arrive through
 * a bean's proxy, so this can't be done with private methods called on
 * `this` inside one class - that's why this is split into four small
 * beans rather than four private methods).
 *
 * Each @Observed call becomes both a Micrometer Timer (order.validate,
 * order.fetch, order.pricing, order.assemble - scraped by Prometheus,
 * graphable in Grafana) and a child span under the request's trace
 * (visible as a waterfall in Zipkin). Same request, same latency - now you
 * can see exactly where it goes.
 */
@Service
public class FixedOrderService {

    private final OrderValidator orderValidator;
    private final OrderDatabaseClient orderDatabaseClient;
    private final PricingClient pricingClient;
    private final OrderResponseAssembler orderResponseAssembler;

    public FixedOrderService(
            OrderValidator orderValidator,
            OrderDatabaseClient orderDatabaseClient,
            PricingClient pricingClient,
            OrderResponseAssembler orderResponseAssembler) {
        this.orderValidator = orderValidator;
        this.orderDatabaseClient = orderDatabaseClient;
        this.pricingClient = pricingClient;
        this.orderResponseAssembler = orderResponseAssembler;
    }

    public Map<String, Object> getOrder(String orderId) {
        orderValidator.validate(orderId);
        Map<String, Object> order = orderDatabaseClient.fetchFromDatabase(orderId);
        double price = pricingClient.getPrice(orderId);
        return orderResponseAssembler.assemble(orderId, order, price);
    }
}
