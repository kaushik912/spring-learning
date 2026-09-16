package com.example.dbioexhaustiondemo.mysql;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("mysql")
@Tag(name = "MySQL Buggy Orders", description = "Artificially slow query over a real TCP connection to MySQL - reproduces pool exhaustion with genuine socket I/O")
public class MySqlBuggyOrderController {

    private final MySqlBuggyOrderRepository repository;

    public MySqlBuggyOrderController(MySqlBuggyOrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/mysql/buggy/orders")
    @Operation(summary = "Look up orders by email; SLEEP() on the server stands in for a slow query")
    public List<CustomerOrder> byEmail(@RequestParam String email,
                                        @RequestParam(defaultValue = "1.5") double delaySeconds) {
        return repository.findByEmailWithArtificialDelay(email, delaySeconds);
    }
}
