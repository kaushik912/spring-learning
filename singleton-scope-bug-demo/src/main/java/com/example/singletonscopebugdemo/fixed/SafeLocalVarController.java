package com.example.singletonscopebugdemo.fixed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * FIX 1: don't store per-request state anywhere shared. Pass it as a method
 * parameter / local variable — each thread's stack is its own, no sharing.
 */
@RestController
@Tag(name = "Fixed - local variable", description = "Per-request state kept as a local variable, never shared")
public class SafeLocalVarController {

    @Operation(summary = "Get order summary (fixed: userId stays a local variable)")
    @GetMapping("/api/safe/local/orders/{userId}")
    public Map<String, Object> getOrder(@PathVariable String userId) throws InterruptedException {
        String resolvedUserId = userId; // local to this thread's stack frame

        Thread.sleep(50);

        return Map.of(
                "requestedUserId", userId,
                "resolvedUserId", resolvedUserId,
                "corrupted", !userId.equals(resolvedUserId));
    }
}
