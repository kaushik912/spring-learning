package com.example.resourceexhaustiondemo.cpu.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a deterministic list of order IDs with a known duplicate rate,
 * so the buggy and fixed duplicate finders can be compared on identical
 * input - same correctness, only speed differs.
 */
public final class OrderIdGenerator {

    private OrderIdGenerator() {
    }

    public static List<String> generate(int count, int duplicateEvery) {
        List<String> orderIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = duplicateEvery > 0 ? i % duplicateEvery : i;
            orderIds.add("order-" + id);
        }
        return orderIds;
    }
}
