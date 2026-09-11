package com.example.singletonscopebugdemo.fixed;

import org.springframework.stereotype.Service;

/**
 * FIX 2: keep the singleton bean, but back the "current user" with a
 * ThreadLocal instead of a plain field. Each thread gets its own slot, so
 * concurrent requests can no longer see each other's value.
 *
 * Useful when other methods deep in the call stack need the current user
 * without threading it through every method signature — e.g. a real app
 * would set this in a servlet filter/interceptor and clear it in a
 * finally block once the request completes, to avoid leaking values into
 * a pooled thread's next request.
 */
@Service
public class ThreadLocalRequestContext {

    private final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    public void setCurrentUserId(String userId) {
        currentUserId.set(userId);
    }

    public String getCurrentUserId() {
        return currentUserId.get();
    }

    public void clear() {
        currentUserId.remove();
    }
}
