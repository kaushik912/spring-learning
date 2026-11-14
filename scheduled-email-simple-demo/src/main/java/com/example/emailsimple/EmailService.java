package com.example.emailsimple;

import com.example.emailsimple.EmailSimpleApplication.DemoProperties;
import com.example.emailsimple.MockEmailClient.EmailSendException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * All the logic. emails_sent row lifecycle:
 *   seed -> PENDING --send ok--> SENT
 *                   --send fails--> FAILED (attempts+1) --sweeper retry--> SENT | FAILED ... until attempts == max
 * Support "resend" puts a row back to FAILED with attempts=0 and a new idempotency key.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailRecordRepository emails;
    private final MockEmailClient client;
    private final DemoProperties props;

    public EmailService(EmailRecordRepository emails, MockEmailClient client, DemoProperties props) {
        this.emails = emails;
        this.client = client;
        this.props = props;
    }

    /** Idempotency key: same on retries (provider dedups), new after a deliberate support resend. */
    private static String idempotencyKey(EmailRecord r) {
        return r.getId().runId() + ":" + r.getId().userId() + ":r" + r.getResendCount();
    }

    /** Step 1: one PENDING row per user. Safe to repeat: existing rows are skipped, the primary key backs it up. */
    public void seed(String runId) {
        for (int userId = 1; userId <= props.userCount(); userId++) {
            EmailKey key = new EmailKey(runId, userId);
            if (!emails.existsById(key)) {
                try {
                    emails.saveAndFlush(new EmailRecord(key)); // plain INSERT (see EmailRecord)
                } catch (DataIntegrityViolationException seededByAnotherRunnerJustNow) {
                    // fine: the row exists, that's all we wanted
                }
            }
        }
    }

    /** Step 2 (daily job): send everything still PENDING (also leftovers from a crashed earlier run). */
    public int sendPending() {
        return sendAll(EmailStatus.PENDING);
    }

    /** Sweeper: retry FAILED rows that still have attempts left. */
    public int retryFailed() {
        return sendAll(EmailStatus.FAILED);
    }

    private int sendAll(EmailStatus status) {
        List<EmailRecord> rows = emails.findRetryable(status, props.maxAttempts());
        for (EmailRecord r : rows) {
            try {
                client.send(idempotencyKey(r), r.getId().userId());
                emails.markSent(r.getId(), Instant.now());
            } catch (EmailSendException e) {
                emails.markFailed(r.getId(), e.getMessage(), Instant.now());
            }
        }
        log.info("{} pass: tried {} rows", status, rows.size());
        return rows.size();
    }

    /** Support tool: customer says "I never got it". Re-queue one user; the sweeper sends it. */
    public boolean resend(String runId, int userId, String requestedBy) {
        return emails.requeueForResend(new EmailKey(runId, userId), requestedBy, Instant.now()) == 1;
    }

    public Map<String, Object> report(String runId) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (EmailStatus s : EmailStatus.values()) {
            long n = emails.countByIdRunIdAndStatus(runId, s);
            if (n > 0) {
                byStatus.put(s.name(), n);
            }
        }
        List<Integer> dead = emails.findByIdRunIdAndStatusAndAttemptsGreaterThanEqualOrderByIdUserId(
                        runId, EmailStatus.FAILED, props.maxAttempts(), Limit.of(20))
                .stream().map(r -> r.getId().userId()).toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", runId);
        r.put("byStatus", byStatus);
        r.put("failedButStillRetrying",
                emails.countByIdRunIdAndStatusAndAttemptsLessThan(runId, EmailStatus.FAILED, props.maxAttempts()));
        r.put("deadUsers(gave up, first 20)", dead);
        r.put("providerUniqueDeliveries", client.deliveredCount());
        r.put("providerCallsByOutcome", client.callsByOutcome());
        return r;
    }
}
