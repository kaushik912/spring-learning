package com.example.duplicatedeliverydemo.fixed.persistent;

import org.springframework.stereotype.Service;

/**
 * Same idea as FixedEventListener's in-memory Set, but the claim lives in a
 * database row instead of a JVM field - so it's still there after a
 * restart, and any fresh instance of this class backed by the same
 * repository sees exactly the same claims (proven in
 * DuplicateDeliveryReproductionTest by constructing a brand-new instance
 * that never talked to the one that made the original claim).
 *
 * check-then-insert (not the atomic insert-and-catch-exception split used
 * in idempotency-key-race-demo) is correct here because Kafka only
 * redelivers a record to the ONE consumer currently owning that partition,
 * sequentially - there's no genuinely concurrent second caller racing this
 * one for the same eventId, unlike concurrent HTTP retries hitting
 * multiple app instances at once. The unique constraint on event_id is
 * still there as a backstop, not as the enforced atomicity mechanism: a
 * rebalance/zombie-consumer edge case could in theory have two consumer
 * processes briefly overlap on the same record, and check-then-insert
 * alone wouldn't atomically guard against that (the redelivery-driven
 * retry still recovers safely on the next attempt, just less cleanly). A
 * deployment that needs to be airtight against that edge case would want
 * the same split-transaction insert-and-catch pattern as
 * idempotency-key-race-demo's IdempotencyInsertService/DbClaimService.
 */
@Service
public class ProcessedEventStore {

    private final ProcessedEventRepository repository;

    public ProcessedEventStore(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * @return true if this call claimed the eventId (first time seen), false
     *         if it was already claimed before - including before a restart.
     */
    public boolean tryClaim(String eventId) {
        if (repository.existsByEventId(eventId)) {
            return false;
        }
        repository.save(new ProcessedEvent(eventId));
        return true;
    }
}
