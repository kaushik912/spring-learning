package com.example.emailsimple;

import com.example.emailsimple.EmailSimpleApplication.DemoProperties;
import com.example.emailsimple.MockEmailClient.EmailSendException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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

    record Row(String runId, int userId, int resendCount) {
        /** Same key on retries (provider dedups), new key after a deliberate support resend. */
        String idempotencyKey() {
            return runId + ":" + userId + ":r" + resendCount;
        }
    }

    private final JdbcTemplate jdbc;
    private final MockEmailClient client;
    private final DemoProperties props;

    public EmailService(JdbcTemplate jdbc, MockEmailClient client, DemoProperties props) {
        this.jdbc = jdbc;
        this.client = client;
        this.props = props;
    }

    /** Step 1: one PENDING row per user. Safe to repeat, the primary key skips existing rows. */
    public void seed(String runId) {
        for (int userId = 1; userId <= props.userCount(); userId++) {
            jdbc.update("""
                    INSERT INTO emails_sent (run_id, user_id, status) VALUES (?, ?, 'PENDING')
                    ON CONFLICT (run_id, user_id) DO NOTHING""", runId, userId);
        }
    }

    /** Step 2 (daily job): send everything still PENDING (also leftovers from a crashed earlier run). */
    public int sendPending() {
        return sendAll("PENDING");
    }

    /** Sweeper: retry FAILED rows that still have attempts left. */
    public int retryFailed() {
        return sendAll("FAILED");
    }

    private int sendAll(String status) {
        List<Row> rows = jdbc.query("""
                SELECT run_id, user_id, resend_count FROM emails_sent
                 WHERE status = ? AND attempts < ? ORDER BY run_id, user_id""",
                (rs, i) -> new Row(rs.getString("run_id"), rs.getInt("user_id"), rs.getInt("resend_count")),
                status, props.maxAttempts());
        for (Row r : rows) {
            try {
                client.send(r.idempotencyKey(), r.userId());
                jdbc.update("UPDATE emails_sent SET status = 'SENT', updated_at = now() WHERE run_id = ? AND user_id = ?",
                        r.runId(), r.userId());
            } catch (EmailSendException e) {
                // never overwrite SENT (another overlapping runner may have succeeded meanwhile)
                jdbc.update("""
                        UPDATE emails_sent SET status = 'FAILED', attempts = attempts + 1, last_error = ?, updated_at = now()
                         WHERE run_id = ? AND user_id = ? AND status <> 'SENT'""",
                        e.getMessage(), r.runId(), r.userId());
            }
        }
        log.info("{} pass: tried {} rows", status, rows.size());
        return rows.size();
    }

    /** Support tool: customer says "I never got it". Re-queue one user; the sweeper sends it. */
    public boolean resend(String runId, int userId, String requestedBy) {
        return jdbc.update("""
                UPDATE emails_sent
                   SET status = 'FAILED', attempts = 0, resend_count = resend_count + 1,
                       resent_by = ?, updated_at = now()
                 WHERE run_id = ? AND user_id = ? AND status IN ('FAILED', 'SENT')""",
                requestedBy, runId, userId) == 1;
    }

    public Map<String, Object> report(String runId) {
        Map<String, Integer> byStatus = new TreeMap<>();
        jdbc.query("SELECT status, COUNT(*) c FROM emails_sent WHERE run_id = ? GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getInt("c"));
                }, runId);
        Integer retryable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'FAILED' AND attempts < ?",
                Integer.class, runId, props.maxAttempts());
        List<Integer> dead = jdbc.queryForList("""
                SELECT user_id FROM emails_sent WHERE run_id = ? AND status = 'FAILED' AND attempts >= ?
                 ORDER BY user_id LIMIT 20""", Integer.class, runId, props.maxAttempts());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", runId);
        r.put("byStatus", byStatus);
        r.put("failedButStillRetrying", retryable);
        r.put("deadUsers(gave up, first 20)", dead);
        r.put("providerUniqueDeliveries", client.deliveredCount());
        r.put("providerCallsByOutcome", client.callsByOutcome());
        return r;
    }
}
