package com.example.resourceexhaustiondemo.cpu.fixed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FastDuplicateFinder {

    /**
     * Same result as SlowDuplicateFinder, computed in a single pass with a
     * HashSet - O(n) instead of O(n^2). No pairwise comparisons, so
     * duration scales linearly with input size instead of quadratically.
     */
    public List<String> findDuplicates(List<String> orderIds) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String orderId : orderIds) {
            if (!seen.add(orderId)) {
                duplicates.add(orderId);
            }
        }
        return new ArrayList<>(duplicates);
    }
}
