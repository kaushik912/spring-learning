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
    @Autowired EmailRecordRepository emails;
    @Autowired ProviderDeliveryRepository deliveries;
    @Autowired ProviderCallRepository calls;

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

        long sent = emails.countByIdRunIdAndStatus(run, EmailStatus.SENT);
        long dead = emails.countByIdRunIdAndStatusAndAttemptsGreaterThanEqual(run, EmailStatus.FAILED, 3);
        assertThat(sent + dead).isEqualTo(1000);                        // nobody left in limbo
        assertThat(dead).isLessThan(100);                                // ~0.3^3 = 2.7% give up

        // exactly one delivery per SENT user, none for dead users
        long delivered = deliveries.countByIdempotencyKeyStartingWith(run + ":");
        long distinctUsers = deliveries.countDistinctUsersByKeyPrefix(run + ":");
        assertThat(delivered).isEqualTo(sent);
        assertThat(distinctUsers).isEqualTo(delivered);

        // the overlap really happened and the idempotency key absorbed it
        long ignored = calls.countByOutcomeAndIdempotencyKeyStartingWith(ProviderCall.Outcome.DUPLICATE_IGNORED, run + ":");
        assertThat(ignored).isPositive();
        System.out.println("t1: sent=" + sent + " dead=" + dead + " duplicateCallsIgnoredByProvider=" + ignored);
    }

    @Test
    void resendOfSentUser_deliversAgainUnderANewIdempotencyKey() {
        String run = "t2";
        client.setFailureRate(0);
        service.seed(run);
        service.sendPending();
        assertThat(emails.countByIdRunIdAndStatus(run, EmailStatus.SENT)).isEqualTo(1000);

        assertThat(service.resend(run, 7, "alice")).isTrue();
        assertThat(service.resend(run, 7, "alice")).isTrue();   // second click bumps the key again
        service.retryFailed();

        assertThat(emails.findById(new EmailKey(run, 7)).orElseThrow().getStatus()).isEqualTo(EmailStatus.SENT);
        // r0 (original) + r2 (after two resends); r1 was never sent because the second resend superseded it
        assertThat(deliveries.countByIdempotencyKeyStartingWith(run + ":7:")).isEqualTo(2);
    }

    @Test
    void deadUser_getsEmailAfterFix_viaResend_othersStayDead() {
        String run = "t3";
        client.setFailureRate(1.0);                 // provider broken (e.g. bad API key)
        service.seed(run);
        service.sendPending();                      // attempt 1
        sweepUntilQuiet();                          // attempts 2, 3 -> everyone gave up
        assertThat(emails.countByIdRunIdAndStatusAndAttemptsGreaterThanEqual(run, EmailStatus.FAILED, 3))
                .isEqualTo(1000);

        client.setFailureRate(0);                   // "we fixed the API key"
        assertThat(service.retryFailed()).isZero(); // sweeper ignores dead rows on its own

        assertThat(service.resend(run, 5, "support")).isTrue();
        sweepUntilQuiet();

        assertThat(emails.countByIdRunIdAndStatus(run, EmailStatus.SENT)).isEqualTo(1);
        assertThat(emails.findById(new EmailKey(run, 5)).orElseThrow().getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(emails.countByIdRunIdAndStatus(run, EmailStatus.FAILED)).isEqualTo(999);
    }
}
