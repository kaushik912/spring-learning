package com.example.resourceexhaustiondemo.cpu.fixed;

import com.example.resourceexhaustiondemo.cpu.shared.OrderIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed duplicates", description = "Finds duplicate order IDs in a single O(n) pass with a HashSet")
public class FixedDuplicateController {

    private final FastDuplicateFinder finder;

    public FixedDuplicateController(FastDuplicateFinder finder) {
        this.finder = finder;
    }

    @GetMapping("/api/fixed/orders/duplicates")
    @Operation(summary = "Find duplicate order IDs in one pass using a HashSet")
    public Map<String, Object> duplicates(@RequestParam(defaultValue = "5000") int count) {
        List<String> orderIds = OrderIdGenerator.generate(count, count / 20);
        long start = System.nanoTime();
        List<String> duplicates = finder.findDuplicates(orderIds);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return Map.of("inputSize", orderIds.size(), "duplicatesFound", duplicates.size(), "elapsedMs", elapsedMs);
    }
}
