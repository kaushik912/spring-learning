package com.example.idempotencykeyracedemo.charge;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Stands in for the actual "call the payment gateway and charge the card"
 * work. Counts how many times a given idempotency key was actually charged,
 * so tests can prove whether a customer got billed once or twice.
 */
@Component
public class ChargeSimulator {

    private final ConcurrentHashMap<String, AtomicInteger> chargeCounts = new ConcurrentHashMap<>();

    public void charge(String idempotencyKey) throws InterruptedException {
        // Simulate gateway/network latency — widens the window in which a
        // second concurrent request can race past an unfinished first one.
        Thread.sleep(50);
        chargeCounts.computeIfAbsent(idempotencyKey, k -> new AtomicInteger()).incrementAndGet();
    }

    public int getChargeCount(String idempotencyKey) {
        AtomicInteger count = chargeCounts.get(idempotencyKey);
        return count == null ? 0 : count.get();
    }
}
