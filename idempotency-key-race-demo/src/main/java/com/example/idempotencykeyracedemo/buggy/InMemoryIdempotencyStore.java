package com.example.idempotencykeyracedemo.buggy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * THE BUG: "have I seen this key?" and "remember this key" are two separate
 * calls. Even though the backing Set is thread-safe on its own, nothing
 * makes the check-then-act SEQUENCE atomic — two threads can both call
 * hasBeenSeen() and get false before either one calls markSeen().
 */
@Component
public class InMemoryIdempotencyStore {

    private final Set<String> seenKeys = Collections.synchronizedSet(new HashSet<>());

    public boolean hasBeenSeen(String idempotencyKey) {
        return seenKeys.contains(idempotencyKey);
    }

    public void markSeen(String idempotencyKey) {
        seenKeys.add(idempotencyKey);
    }
}
