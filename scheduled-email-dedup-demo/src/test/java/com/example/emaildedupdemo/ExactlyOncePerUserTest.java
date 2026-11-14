package com.example.emaildedupdemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 1000 users, 5 concurrent publishers (simulating a ShedLock failure / several instances all publishing),
 * 4 workers with injected provider 503s and "crash after send". Invariant: exactly one delivery per user.
 */
@SpringBootTest
class ExactlyOncePerUserTest {

    // Started once and left to Ryuk at JVM exit: the cached Spring context (with its schedulers/workers)
    // outlives the test class, so stopping the container per class would spam errors at teardown.
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static {
        postgres.start();
    }

    @Autowired EmailPublisher publisher;
    @Autowired OutboxRepository outbox;
    @Autowired MockEmailProvider provider;
    @Autowired JdbcTemplate jdbc;

    @Test
    void exactlyOneEmailPerUser_despiteRacingPublishersFailuresAndCrashes() throws Exception {
        String runId = "test-run";

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> publisher.publish(runId)));
        }
        int totalInserted = 0;
        for (Future<Integer> f : futures) {
            totalInserted += f.get();
        }
        pool.shutdown();

        // 5 publishers raced, but each (run,user) row was inserted exactly once.
        assertThat(totalInserted).isEqualTo(1000);

        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> outbox.countByStatus(runId).getOrDefault("SENT", 0) == 1000);

        assertThat(provider.deliveredCount(runId)).isEqualTo(1000);
        Integer usersWithMultipleDeliveries = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT user_id FROM provider_delivered WHERE run_id = ?
                                       GROUP BY user_id HAVING COUNT(*) > 1)""", Integer.class, runId);
        assertThat(usersWithMultipleDeliveries).isZero();

        // Chaos actually happened (otherwise the test proves nothing).
        var calls = provider.callsByOutcome(runId);
        assertThat(calls.getOrDefault("TRANSIENT_ERROR", 0)).isPositive();
        System.out.println("provider calls: " + calls + ", maxAttempts=" + outbox.maxAttempts(runId));
    }
}
