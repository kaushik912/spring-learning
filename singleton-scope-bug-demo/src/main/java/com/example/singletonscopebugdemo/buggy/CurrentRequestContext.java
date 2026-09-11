package com.example.singletonscopebugdemo.buggy;

import org.springframework.stereotype.Service;

/**
 * THE BUG: default @Service scope is singleton, so this one instance is
 * shared by every concurrent request/thread. Storing the "current" user id
 * as an instance field turns it into shared mutable state — a race.
 */
@Service
public class CurrentRequestContext {

    private String currentUserId;

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }
}
