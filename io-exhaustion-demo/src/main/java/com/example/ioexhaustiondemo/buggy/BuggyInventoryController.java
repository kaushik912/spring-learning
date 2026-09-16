package com.example.ioexhaustiondemo.buggy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Buggy inventory check", description = "Checks inventory availability by blocking the Tomcat request thread on a synchronous downstream call")
public class BuggyInventoryController {

    private final SlowDownstreamService service;

    public BuggyInventoryController(SlowDownstreamService service) {
        this.service = service;
    }

    @GetMapping("/api/buggy/inventory/check")
    @Operation(summary = "Check inventory availability, blocking the request thread on the downstream call")
    public Map<String, Object> check(@RequestParam(defaultValue = "300") long delayMs) {
        return service.checkAvailability(delayMs);
    }
}
