package com.example.emailkafka;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The small amount of state we keep in Postgres: what was already sent/dead, and which runs were published. */
@Repository
public class ResultStore {

    private final JdbcTemplate jdbc;

    public ResultStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- consumer side ----

    /**
     * Dedup check: was this request, or a NEWER one for the same user (higher resendCount), already sent?
     * The "newer" part matters: if a support resend (r1) went out, a stale copy of the original request (r0)
     * showing up later (e.g. someone re-published the run) must not email the user again.
     */
    public boolean alreadySent(EmailRequest r) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_results
                 WHERE run_id = ? AND user_id = ? AND resend_count >= ? AND status = 'SENT'""",
                Integer.class, r.runId(), r.userId(), r.resendCount());
        return n != null && n > 0;
    }

    public void markSent(EmailRequest r, String instance) {
        // this user is no longer dead (e.g. a resend after the dead-letter)
        jdbc.update("DELETE FROM email_results WHERE run_id = ? AND user_id = ? AND status = 'DEAD'",
                r.runId(), r.userId());
        jdbc.update("""
                INSERT INTO email_results (run_id, user_id, resend_count, status, instance)
                VALUES (?, ?, ?, 'SENT', ?) ON CONFLICT (run_id, user_id, resend_count) DO NOTHING""",
                r.runId(), r.userId(), r.resendCount(), instance);
    }

    /** Called for every record that ends up in the dead-letter topic. */
    public void markDead(EmailRequest r, String instance) {
        // a duplicate copy of this request may have succeeded in the meantime: then the user is not dead
        Integer sentSince = jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_results
                 WHERE run_id = ? AND user_id = ? AND status = 'SENT' AND resend_count >= ?""",
                Integer.class, r.runId(), r.userId(), r.resendCount());
        if (sentSince != null && sentSince > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO email_results (run_id, user_id, resend_count, status, instance)
                VALUES (?, ?, ?, 'DEAD', ?) ON CONFLICT (run_id, user_id, resend_count) DO NOTHING""",
                r.runId(), r.userId(), r.resendCount(), instance);
    }

    // ---- publisher side ----

    public boolean runPublished(String runId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM email_runs WHERE run_id = ?", Integer.class, runId);
        return n != null && n > 0;
    }

    public void markRunPublished(String runId, String instance) {
        jdbc.update("INSERT INTO email_runs (run_id, published_by) VALUES (?, ?) ON CONFLICT (run_id) DO NOTHING",
                runId, instance);
    }

    /** Next resendCount for a user: one above the highest we have a result for (0 if none). */
    public int nextResendCount(String runId, int userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(resend_count), -1) + 1 FROM email_results WHERE run_id = ? AND user_id = ?",
                Integer.class, runId, userId);
        return n == null ? 0 : n;
    }

    // ---- reporting ----

    public int distinctUsers(String runId, String status) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM email_results WHERE run_id = ? AND status = ?",
                Integer.class, runId, status);
        return n == null ? 0 : n;
    }

    public Map<String, Integer> sentByInstance(String runId) {
        Map<String, Integer> out = new TreeMap<>();
        jdbc.query("SELECT instance, COUNT(*) c FROM email_results WHERE run_id = ? AND status = 'SENT' GROUP BY instance",
                rs -> {
                    out.put(rs.getString("instance"), rs.getInt("c"));
                }, runId);
        return out;
    }

    public List<Integer> deadUsers(String runId) {
        return jdbc.queryForList(
                "SELECT DISTINCT user_id FROM email_results WHERE run_id = ? AND status = 'DEAD' ORDER BY user_id LIMIT 20",
                Integer.class, runId);
    }
}
