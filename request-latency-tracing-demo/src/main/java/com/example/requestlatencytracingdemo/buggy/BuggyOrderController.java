package com.example.requestlatencytracingdemo.buggy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Buggy", description = "Slow endpoint with no per-step observability")
public class BuggyOrderController {

    private final BuggyOrderService buggyOrderService;

    public BuggyOrderController(BuggyOrderService buggyOrderService) {
        this.buggyOrderService = buggyOrderService;
    }

    @Operation(summary = "Get order (buggy: slow, and you can't see which internal step is why)")
    @GetMapping("/api/buggy/orders/{orderId}")
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        return buggyOrderService.getOrder(orderId);
    }
}
