package com.example.idempotencykeyracedemo.buggy;

import com.example.idempotencykeyracedemo.charge.ChargeSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates a payment endpoint idempotency check that reads then writes in
 * two separate steps — exactly the pattern that double-charges a customer
 * when two identical retries race.
 */
@RestController
@Tag(name = "Buggy", description = "Check-then-act idempotency check — races under concurrent retries")
public class BuggyPaymentController {

    private final InMemoryIdempotencyStore idempotencyStore;
    private final ChargeSimulator chargeSimulator;

    public BuggyPaymentController(InMemoryIdempotencyStore idempotencyStore, ChargeSimulator chargeSimulator) {
        this.idempotencyStore = idempotencyStore;
        this.chargeSimulator = chargeSimulator;
    }

    @Operation(summary = "Process payment (buggy: separate check-then-act idempotency check)")
    @PostMapping("/api/buggy/payments/{idempotencyKey}")
    public Map<String, Object> processPayment(@PathVariable String idempotencyKey) throws InterruptedException {
        if (!idempotencyStore.hasBeenSeen(idempotencyKey)) {
            chargeSimulator.charge(idempotencyKey);
            idempotencyStore.markSeen(idempotencyKey);
            return Map.of("idempotencyKey", idempotencyKey, "processed", true);
        }
        return Map.of("idempotencyKey", idempotencyKey, "processed", false, "reason", "duplicate");
    }
}
