package com.example.dbioexhaustiondemo.buggy;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!mysql")
@Tag(name = "Buggy Orders", description = "Unindexed full-scan lookup - reproduces DB connection pool exhaustion")
public class BuggyOrderController {

    private final BuggyOrderRepository repository;

    public BuggyOrderController(BuggyOrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/buggy/orders")
    @Operation(summary = "Look up orders by email; index is defeated by wrapping the column in UPPER()")
    public List<CustomerOrder> byEmail(@RequestParam String email) {
        return repository.findByEmailIgnoreCaseSlow(email);
    }
}
