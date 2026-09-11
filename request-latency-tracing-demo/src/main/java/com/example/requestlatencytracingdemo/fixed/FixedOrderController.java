package com.example.requestlatencytracingdemo.fixed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed - Observability", description = "Same slow endpoint, but @Observed makes the slow step visible")
public class FixedOrderController {

    private final FixedOrderService fixedOrderService;

    public FixedOrderController(FixedOrderService fixedOrderService) {
        this.fixedOrderService = fixedOrderService;
    }

    @Operation(summary = "Get order (fixed: per-step timers + trace spans reveal which step is slow)")
    @GetMapping("/api/fixed/orders/{orderId}")
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        return fixedOrderService.getOrder(orderId);
    }
}
