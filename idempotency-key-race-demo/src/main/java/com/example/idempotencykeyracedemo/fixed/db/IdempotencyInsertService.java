package com.example.idempotencykeyracedemo.fixed.db;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as its own bean/transaction boundary on purpose: once a flush throws
 * a constraint violation, Hibernate marks that transaction rollback-only at
 * the ORM level — catching the exception inside the same @Transactional
 * method doesn't help, the commit still fails with UnexpectedRollbackException.
 * Letting the failure propagate out of THIS transaction (a clean, expected
 * rollback) and catching it in the caller (DbClaimService, uninvolved in
 * this transaction) avoids that.
 */
@Service
public class IdempotencyInsertService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyInsertService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void insert(String idempotencyKey) {
        repository.saveAndFlush(new IdempotencyRecord(idempotencyKey));
    }
}
