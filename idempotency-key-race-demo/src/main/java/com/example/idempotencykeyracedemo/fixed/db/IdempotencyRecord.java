package com.example.idempotencykeyracedemo.fixed.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * The unique constraint on idempotency_key is the fix: it turns "claim this
 * key" into a single atomic operation at the database level. Two concurrent
 * inserts for the same key can both be attempted, but only one can commit —
 * the second always fails with a constraint violation.
 */
@Entity
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
