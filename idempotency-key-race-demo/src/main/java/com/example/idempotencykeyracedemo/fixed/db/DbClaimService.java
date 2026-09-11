package com.example.idempotencykeyracedemo.fixed.db;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * FIX: the "claim this key" step is now a single atomic insert. Two
 * concurrent calls can both attempt the insert, but the unique constraint
 * on idempotency_key means the database itself guarantees only one commits.
 */
@Service
public class DbClaimService {

    private final IdempotencyInsertService insertService;

    public DbClaimService(IdempotencyInsertService insertService) {
        this.insertService = insertService;
    }

    /**
     * @return true if this call won the race and claimed the key, false if
     *         another request already claimed it first.
     */
    public boolean tryClaim(String idempotencyKey) {
        try {
            insertService.insert(idempotencyKey);
            return true;
        } catch (DataIntegrityViolationException duplicateKey) {
            return false;
        }
    }
}
