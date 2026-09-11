package com.example.singletonscopebugdemo.buggy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates an endpoint that stashes the current user id on a shared
 * singleton field, does some work, then reads it back — exactly the
 * pattern that races under concurrent load.
 */
@RestController
@Tag(name = "Buggy", description = "Singleton mutable field — races under concurrent load")
public class BuggyOrderController {

    private final CurrentRequestContext currentRequestContext;

    public BuggyOrderController(CurrentRequestContext currentRequestContext) {
        this.currentRequestContext = currentRequestContext;
    }

    @Operation(summary = "Get order summary (buggy: shared singleton field holds the user id)")
    @GetMapping("/api/buggy/orders/{userId}")
    public Map<String, Object> getOrder(@PathVariable String userId) throws InterruptedException {
        currentRequestContext.setCurrentUserId(userId);

        // Simulate work (DB call, downstream service, etc.) that gives other
        // concurrent requests a window to overwrite currentUserId first.
        Thread.sleep(50);

        String resolvedUserId = currentRequestContext.getCurrentUserId();
        return Map.of(
                "requestedUserId", userId,
                "resolvedUserId", resolvedUserId,
                "corrupted", !userId.equals(resolvedUserId));
    }
}
