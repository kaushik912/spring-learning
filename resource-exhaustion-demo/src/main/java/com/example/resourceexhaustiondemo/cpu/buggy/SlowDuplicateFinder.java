package com.example.resourceexhaustiondemo.cpu.buggy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SlowDuplicateFinder {

    /**
     * Finds duplicate order IDs by comparing every pair - O(n^2). Correct,
     * and fast enough with a handful of orders that it looks fine in a demo
     * or a small test dataset. It's the same "healthy but wrong" shape as
     * the memory bug: nothing throws, it just burns one CPU core for longer
     * and longer as order volume grows, with no ceiling.
     */
    public List<String> findDuplicates(List<String> orderIds) {
        List<String> duplicates = new ArrayList<>();
        for (int i = 0; i < orderIds.size(); i++) {
            for (int j = i + 1; j < orderIds.size(); j++) {
                String candidate = orderIds.get(i);
                if (candidate.equals(orderIds.get(j)) && !duplicates.contains(candidate)) {
                    duplicates.add(candidate);
                }
            }
        }
        return duplicates;
    }
}
