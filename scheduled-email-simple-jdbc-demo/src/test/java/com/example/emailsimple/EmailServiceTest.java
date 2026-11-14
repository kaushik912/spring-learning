package com.example.emailsimple;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class EmailServiceTest {

    // Started once, left to Ryuk at JVM exit (cached Spring context outlives the test class).
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static {
        postgres.start();
    }

    @Autowired EmailService service;
    @Autowired MockEmailClient client;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void flakyProvider() {
        client.setFailureRate(0.3);
    }

    /** Retry sweeps until nothing is retryable (each pass either succeeds or burns an attempt, so it ends). */
    private void sweepUntilQuiet() {
        while (service.retryFailed() > 0) {
            // keep going
        }
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void overlappingRunners_doNotDeliverTwice_andFailuresRetriedUpToMax() throws Exception {
        String run = "t1";
        service.seed(run);

        // Two runners at once = what happens if the ShedLock expires mid-run (or a manual trigger).
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> a = pool.submit(() -> {
            go.await();
            return service.sendPending();
        });
        Future<Integer> b = pool.submit(() -> {
            go.await();
            return service.sendPending();
        });
        go.countDown();
        a.get();
        b.get();
        pool.shutdown();

        sweepUntilQuiet();

        int sent = count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'SENT'", run);
        int dead = count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'FAILED' AND attempts >= 3", run);
        assertThat(sent + dead).isEqualTo(1000);                        // nobody left in limbo
        assertThat(dead).isLessThan(100);                                // ~0.3^3 = 2.7% give up

        // exactly one delivery per SENT user, none for dead users
        int delivered = count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":%");
        int distinctUsers = count("SELECT COUNT(DISTINCT user_id) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":%");
        assertThat(delivered).isEqualTo(sent);
        assertThat(distinctUsers).isEqualTo(delivered);

        // the overlap really happened and the idempotency key absorbed it (counted for THIS run only)
        int ignored = count("SELECT COUNT(*) FROM provider_calls WHERE outcome = 'DUPLICATE_IGNORED' AND idempotency_key LIKE ?",
                run + ":%");
        assertThat(ignored).isPositive();
        System.out.println("t1: sent=" + sent + " dead=" + dead + " duplicateCallsIgnoredByProvider=" + ignored);
    }

    @Test
    void resendOfSentUser_deliversAgainUnderANewIdempotencyKey() {
        String run = "t2";
        client.setFailureRate(0);
        service.seed(run);
        service.sendPending();
        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'SENT'", run)).isEqualTo(1000);

        assertThat(service.resend(run, 7, "alice")).isTrue();
        assertThat(service.resend(run, 7, "alice")).isTrue();   // second click bumps the key again
        service.retryFailed();

        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND user_id = 7 AND status = 'SENT'", run)).isEqualTo(1);
        // r0 (original) + r2 (after two resends); r1 was never sent because the second resend superseded it
        assertThat(count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":7:%")).isEqualTo(2);
    }

    @Test
    void deadUser_getsEmailAfterFix_viaResend_othersStayDead() {
        String run = "t3";
        client.setFailureRate(1.0);                 // provider broken (e.g. bad API key)
        service.seed(run);
        service.sendPending();                      // attempt 1
        sweepUntilQuiet();                          // attempts 2, 3 -> everyone gave up
        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'FAILED' AND attempts = 3", run))
                .isEqualTo(1000);

        client.setFailureRate(0);                   // "we fixed the API key"
        assertThat(service.retryFailed()).isZero(); // sweeper ignores dead rows on its own

        assertThat(service.resend(run, 5, "support")).isTrue();
        sweepUntilQuiet();

        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'SENT'", run)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND user_id = 5 AND status = 'SENT'", run)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM emails_sent WHERE run_id = ? AND status = 'FAILED'", run)).isEqualTo(999);
    }
}
