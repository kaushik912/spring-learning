package com.example.resourceexhaustiondemo.memory.buggy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Buggy report", description = "Loads the whole report into memory before responding")
public class MemoryHogController {

    private final MemoryHogService service;

    public MemoryHogController(MemoryHogService service) {
        this.service = service;
    }

    @GetMapping("/api/buggy/report")
    @Operation(summary = "Build a report by materializing every row into one List up front")
    public Map<String, Object> report(@RequestParam int rows) {
        List<byte[]> report = service.buildReport(rows);
        long totalBytes = report.stream().mapToLong(row -> row.length).sum();
        return Map.of("rows", report.size(), "totalBytes", totalBytes);
    }
}
