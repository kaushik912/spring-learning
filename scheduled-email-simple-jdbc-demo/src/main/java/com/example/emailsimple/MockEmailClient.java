package com.example.emailsimple;

import com.example.emailsimple.EmailSimpleApplication.DemoProperties;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fake email API. Randomly fails (503). Like real providers it honours an idempotency key:
 * same key = delivered once, repeats are acknowledged but ignored.
 */
@Component
public class MockEmailClient {

    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String msg) {
            super(msg);
        }
    }

    private final JdbcTemplate jdbc;
    private volatile double failureRate;

    public MockEmailClient(JdbcTemplate jdbc, DemoProperties props) {
        this.jdbc = jdbc;
        this.failureRate = props.providerFailureRate();
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate; // tests use this to "fix the provider"
    }

    public void send(String idempotencyKey, int userId) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            log(idempotencyKey, "TRANSIENT_ERROR");
            throw new EmailSendException("503 from provider");
        }
        // PK on idempotency_key: 1 row inserted = first time seen, 0 = a repeat -> acknowledge but ignore
        int inserted = jdbc.update("""
                INSERT INTO provider_delivered (idempotency_key, user_id) VALUES (?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING""", idempotencyKey, userId);
        log(idempotencyKey, inserted == 1 ? "DELIVERED" : "DUPLICATE_IGNORED");
    }

    private void log(String key, String outcome) {
        jdbc.update("INSERT INTO provider_calls (idempotency_key, outcome) VALUES (?, ?)", key, outcome);
    }

    public int deliveredCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM provider_delivered", Integer.class);
    }

    public Map<String, Integer> callsByOutcome() {
        Map<String, Integer> out = new TreeMap<>();
        jdbc.query("SELECT outcome, COUNT(*) c FROM provider_calls GROUP BY outcome",
                rs -> {
                    out.put(rs.getString("outcome"), rs.getInt("c"));
                });
        return out;
    }
}
