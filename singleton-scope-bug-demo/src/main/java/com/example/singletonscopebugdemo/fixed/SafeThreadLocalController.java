package com.example.singletonscopebugdemo.fixed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed - ThreadLocal", description = "Per-request state kept in a ThreadLocal on the singleton bean")
public class SafeThreadLocalController {

    private final ThreadLocalRequestContext currentRequestContext;

    public SafeThreadLocalController(ThreadLocalRequestContext currentRequestContext) {
        this.currentRequestContext = currentRequestContext;
    }

    @Operation(summary = "Get order summary (fixed: userId kept in a ThreadLocal)")
    @GetMapping("/api/safe/threadlocal/orders/{userId}")
    public Map<String, Object> getOrder(@PathVariable String userId) throws InterruptedException {
        try {
            currentRequestContext.setCurrentUserId(userId);

            Thread.sleep(50);

            String resolvedUserId = currentRequestContext.getCurrentUserId();
            return Map.of(
                    "requestedUserId", userId,
                    "resolvedUserId", resolvedUserId,
                    "corrupted", !userId.equals(resolvedUserId));
        } finally {
            // Must clear: embedded servers reuse threads from a pool, so a
            // stale value would otherwise leak into a later, unrelated request.
            currentRequestContext.clear();
        }
    }
}
