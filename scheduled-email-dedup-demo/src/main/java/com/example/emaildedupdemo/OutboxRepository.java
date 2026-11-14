package com.example.emaildedupdemo;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The outbox table is the work queue between the publisher and the workers. */
@Repository
public class OutboxRepository {

    public record Job(long id, String runId, int userId) {
        public String idempotencyKey() {
            return runId + ":" + userId;
        }
    }

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent enqueue of one user. UNIQUE(run_id, user_id) + ON CONFLICT DO NOTHING makes a second
     * publisher (ShedLock failed / manual trigger / retry after crash) a no-op instead of a duplicate.
     * Returns true if this call inserted the row, false if it already existed.
     */
    public boolean enqueue(String runId, int userId) {
        return jdbc.update("""
                INSERT INTO email_outbox (run_id, user_id, status) VALUES (?, ?, 'PENDING')
                ON CONFLICT (run_id, user_id) DO NOTHING
                """, runId, userId) == 1;
    }

    /**
     * Claim up to {@code limit} PENDING rows in a single atomic statement. FOR UPDATE SKIP LOCKED makes
     * concurrent workers skip rows another worker is claiming, so they never contend for or double-take a row.
     */
    public List<Job> claimBatch(String worker, int limit) {
        return jdbc.query("""
                UPDATE email_outbox
                   SET status = 'CLAIMED', claimed_by = ?, claimed_at = now(), attempts = attempts + 1
                 WHERE id IN (SELECT id FROM email_outbox WHERE status = 'PENDING'
                               ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, run_id, user_id""",
                (rs, i) -> new Job(rs.getLong("id"), rs.getString("run_id"), rs.getInt("user_id")),
                worker, limit);
    }

    /** Only the current owner may mark SENT (guards against a stale worker after the reaper reassigned). */
    public boolean markSent(long id, String worker) {
        return jdbc.update("""
                UPDATE email_outbox SET status = 'SENT', sent_at = now()
                 WHERE id = ? AND status = 'CLAIMED' AND claimed_by = ?""", id, worker) == 1;
    }

    /** Transient failure: back to PENDING for retry. */
    public void release(long id, String worker) {
        jdbc.update("""
                UPDATE email_outbox SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL
                 WHERE id = ? AND status = 'CLAIMED' AND claimed_by = ?""", id, worker);
    }

    /** Reaper: rows stuck CLAIMED (worker died / paused) go back to PENDING. */
    public int reapStale(long claimTimeoutMs) {
        return jdbc.update("""
                UPDATE email_outbox SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL
                 WHERE status = 'CLAIMED' AND claimed_at < now() - (? * interval '1 millisecond')""",
                claimTimeoutMs);
    }

    public Map<String, Integer> countByStatus(String runId) {
        Map<String, Integer> out = new TreeMap<>();
        jdbc.query("SELECT status, COUNT(*) c FROM email_outbox WHERE run_id = ? GROUP BY status",
                rs -> {
                    out.put(rs.getString("status"), rs.getInt("c"));
                }, runId);
        return out;
    }

    /** SENT rows per instance (claimed_by is "instance-port/worker-n"). */
    public Map<String, Integer> sentByInstance(String runId) {
        Map<String, Integer> out = new TreeMap<>();
        jdbc.query("""
                SELECT split_part(claimed_by, '/', 1) inst, COUNT(*) c
                  FROM email_outbox WHERE run_id = ? AND status = 'SENT' GROUP BY 1""",
                rs -> {
                    out.put(rs.getString("inst"), rs.getInt("c"));
                }, runId);
        return out;
    }

    public int maxAttempts(String runId) {
        Integer v = jdbc.queryForObject("SELECT COALESCE(MAX(attempts), 0) FROM email_outbox WHERE run_id = ?",
                Integer.class, runId);
        return v == null ? 0 : v;
    }
}
