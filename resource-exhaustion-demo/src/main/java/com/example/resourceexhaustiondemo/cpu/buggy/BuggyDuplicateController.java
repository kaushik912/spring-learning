package com.example.resourceexhaustiondemo.cpu.buggy;

import com.example.resourceexhaustiondemo.cpu.shared.OrderIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Buggy duplicates", description = "Finds duplicate order IDs with an O(n^2) nested-loop scan")
public class BuggyDuplicateController {

    private final SlowDuplicateFinder finder;

    public BuggyDuplicateController(SlowDuplicateFinder finder) {
        this.finder = finder;
    }

    @GetMapping("/api/buggy/orders/duplicates")
    @Operation(summary = "Find duplicate order IDs by comparing every pair")
    public Map<String, Object> duplicates(@RequestParam(defaultValue = "5000") int count) {
        List<String> orderIds = OrderIdGenerator.generate(count, count / 20);
        long start = System.nanoTime();
        List<String> duplicates = finder.findDuplicates(orderIds);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return Map.of("inputSize", orderIds.size(), "duplicatesFound", duplicates.size(), "elapsedMs", elapsedMs);
    }
}
