package com.example.emailsimple;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mock provider side: one row per idempotency key it has accepted. The primary key is its dedup. */
@Entity
@Table(name = "provider_delivered")
public class ProviderDelivery {

    @Id
    private String idempotencyKey;
    private int userId;
    private Instant deliveredAt;

    protected ProviderDelivery() {
    }

    public ProviderDelivery(String idempotencyKey, int userId) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.deliveredAt = Instant.now();
    }
}
