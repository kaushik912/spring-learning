package com.example.idempotencykeyracedemo.fixed.redis;

import com.example.idempotencykeyracedemo.charge.ChargeSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fixed - Redis SETNX", description = "Atomic claim via Redis SET ... NX")
public class FixedRedisPaymentController {

    private final RedisClaimService claimService;
    private final ChargeSimulator chargeSimulator;

    public FixedRedisPaymentController(RedisClaimService claimService, ChargeSimulator chargeSimulator) {
        this.claimService = claimService;
        this.chargeSimulator = chargeSimulator;
    }

    @Operation(summary = "Process payment (fixed: Redis SETNX claims the key atomically)")
    @PostMapping("/api/fixed/redis/payments/{idempotencyKey}")
    public Map<String, Object> processPayment(@PathVariable String idempotencyKey) throws InterruptedException {
        if (claimService.tryClaim(idempotencyKey)) {
            chargeSimulator.charge(idempotencyKey);
            return Map.of("idempotencyKey", idempotencyKey, "processed", true);
        }
        return Map.of("idempotencyKey", idempotencyKey, "processed", false, "reason", "duplicate");
    }
}
