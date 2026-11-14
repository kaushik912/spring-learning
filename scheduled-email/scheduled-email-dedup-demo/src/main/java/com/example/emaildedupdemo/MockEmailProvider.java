package com.example.emaildedupdemo;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fake email API (think SES / SendGrid). Like real ones it supports an idempotency key:
 * the same key delivers at most once, repeats are acknowledged but ignored.
 * State lives in the shared DB so separate JVMs talk to the same "provider".
 */
@Component
public class MockEmailProvider {

    public static class TransientProviderException extends RuntimeException {
        public TransientProviderException(String msg) {
            super(msg);
        }
    }

    public enum Outcome { DELIVERED, DUPLICATE_IGNORED }

    private final JdbcTemplate jdbc;
    private final DemoProperties props;

    public MockEmailProvider(JdbcTemplate jdbc, DemoProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public Outcome send(String idempotencyKey, String runId, int userId) {
        sleep(props.providerLatencyMs());
        if (ThreadLocalRandom.current().nextDouble() < props.providerFailureRate()) {
            logCall(idempotencyKey, runId, "TRANSIENT_ERROR");
            throw new TransientProviderException("503 from provider for " + idempotencyKey);
        }
        // PK on idempotency_key: 1 row inserted = first time seen, 0 = repeat -> acknowledge but ignore.
        int inserted = jdbc.update("""
                INSERT INTO provider_delivered (idempotency_key, run_id, user_id) VALUES (?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING""", idempotencyKey, runId, userId);
        Outcome outcome = inserted == 1 ? Outcome.DELIVERED : Outcome.DUPLICATE_IGNORED;
        logCall(idempotencyKey, runId, outcome.name());
        return outcome;
    }

    private void logCall(String key, String runId, String outcome) {
        jdbc.update("INSERT INTO provider_calls (idempotency_key, run_id, outcome, instance) VALUES (?, ?, ?, ?)",
                key, runId, outcome, props.instanceId());
    }

    public int deliveredCount(String runId) {
        Integer v = jdbc.queryForObject("SELECT COUNT(*) FROM provider_delivered WHERE run_id = ?", Integer.class, runId);
        return v == null ? 0 : v;
    }

    public Map<String, Integer> callsByOutcome(String runId) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        jdbc.query("SELECT outcome, COUNT(*) c FROM provider_calls WHERE run_id = ? GROUP BY outcome",
                rs -> {
                    out.put(rs.getString("outcome"), rs.getInt("c"));
                }, runId);
        return out;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
