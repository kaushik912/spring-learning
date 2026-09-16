package com.example.dbioexhaustiondemo.fixed;

import com.example.dbioexhaustiondemo.shared.CustomerOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Fixed Orders", description = "Indexed lookup - same pool, no exhaustion")
public class FixedOrderController {

    private final FixedOrderRepository repository;

    public FixedOrderController(FixedOrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/fixed/orders")
    @Operation(summary = "Look up orders by email using the indexed column directly")
    public List<CustomerOrder> byEmail(@RequestParam String email) {
        return repository.findByCustomerEmail(email);
    }
}
