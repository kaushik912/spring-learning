package com.example.emailsimple;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailRecordRepository extends JpaRepository<EmailRecord, EmailKey> {

    // ---- reads: the method name is the query ----
    long countByIdRunIdAndStatus(String runId, EmailStatus status);

    long countByIdRunIdAndStatusAndAttemptsLessThan(String runId, EmailStatus status, int attempts);

    long countByIdRunIdAndStatusAndAttemptsGreaterThanEqual(String runId, EmailStatus status, int attempts);

    List<EmailRecord> findByIdRunIdAndStatusAndAttemptsGreaterThanEqualOrderByIdUserId(
            String runId, EmailStatus status, int attempts, Limit limit);

    /** Rows with this status that still have attempts left, any run. */
    @Query("select e from EmailRecord e where e.status = :status and e.attempts < :max "
            + "order by e.id.runId, e.id.userId")
    List<EmailRecord> findRetryable(@Param("status") EmailStatus status, @Param("max") int max);

    // ---- guarded updates. Conditional UPDATEs (not load-modify-save) so two overlapping runners can't
    // ---- overwrite each other's result. Each returns the number of rows changed.
    @Transactional
    @Modifying
    @Query("update EmailRecord e set e.status = com.example.emailsimple.EmailStatus.SENT, e.updatedAt = :now "
            + "where e.id = :id")
    int markSent(@Param("id") EmailKey id, @Param("now") Instant now);

    /** Never overwrites SENT (an overlapping runner may have succeeded meanwhile). */
    @Transactional
    @Modifying
    @Query("update EmailRecord e set e.status = com.example.emailsimple.EmailStatus.FAILED, "
            + "e.attempts = e.attempts + 1, e.lastError = :error, e.updatedAt = :now "
            + "where e.id = :id and e.status <> com.example.emailsimple.EmailStatus.SENT")
    int markFailed(@Param("id") EmailKey id, @Param("error") String error, @Param("now") Instant now);

    /** Support resend: back to FAILED with a fresh set of attempts and a new idempotency key. */
    @Transactional
    @Modifying
    @Query("update EmailRecord e set e.status = com.example.emailsimple.EmailStatus.FAILED, e.attempts = 0, "
            + "e.resendCount = e.resendCount + 1, e.resentBy = :by, e.updatedAt = :now "
            + "where e.id = :id and e.status in (com.example.emailsimple.EmailStatus.FAILED, "
            + "com.example.emailsimple.EmailStatus.SENT)")
    int requeueForResend(@Param("id") EmailKey id, @Param("by") String by, @Param("now") Instant now);
}
