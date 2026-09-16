package com.example.ioexhaustiondemo.fixed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed inventory check", description = "Checks inventory availability on a dedicated bounded executor, freeing the Tomcat thread immediately")
public class FixedInventoryController {

    private final AsyncDownstreamService service;

    public FixedInventoryController(AsyncDownstreamService service) {
        this.service = service;
    }

    @GetMapping("/api/fixed/inventory/check")
    @Operation(summary = "Check inventory availability, offloading the downstream call to a dedicated executor")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> check(@RequestParam(defaultValue = "300") long delayMs) {
        return service.checkAvailabilityAsync(delayMs).thenApply(ResponseEntity::ok);
    }
}
