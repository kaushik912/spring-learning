package com.example.emailsimple;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every API call the mock provider received (to show retries and ignored duplicates). */
@Entity
@Table(name = "provider_calls")
public class ProviderCall {

    public enum Outcome { DELIVERED, DUPLICATE_IGNORED, TRANSIENT_ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    private Outcome outcome;
    private Instant calledAt;

    protected ProviderCall() {
    }

    public ProviderCall(String idempotencyKey, Outcome outcome) {
        this.idempotencyKey = idempotencyKey;
        this.outcome = outcome;
        this.calledAt = Instant.now();
    }
}
