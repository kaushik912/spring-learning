package com.example.idempotencykeyracedemo.fixed.db;

import com.example.idempotencykeyracedemo.charge.ChargeSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed - DB unique constraint", description = "Atomic claim via a unique-constrained insert")
public class FixedDbPaymentController {

    private final DbClaimService claimService;
    private final ChargeSimulator chargeSimulator;

    public FixedDbPaymentController(DbClaimService claimService, ChargeSimulator chargeSimulator) {
        this.claimService = claimService;
        this.chargeSimulator = chargeSimulator;
    }

    @Operation(summary = "Process payment (fixed: DB unique constraint claims the key atomically)")
    @PostMapping("/api/fixed/db/payments/{idempotencyKey}")
    public Map<String, Object> processPayment(@PathVariable String idempotencyKey) throws InterruptedException {
        if (claimService.tryClaim(idempotencyKey)) {
            chargeSimulator.charge(idempotencyKey);
            return Map.of("idempotencyKey", idempotencyKey, "processed", true);
        }
        return Map.of("idempotencyKey", idempotencyKey, "processed", false, "reason", "duplicate");
    }
}
