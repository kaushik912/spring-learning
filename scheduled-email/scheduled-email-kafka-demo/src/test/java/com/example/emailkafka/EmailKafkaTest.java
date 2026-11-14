package com.example.emailkafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class EmailKafkaTest {

    // Started once, left to Ryuk at JVM exit (the cached Spring context's consumers outlive the test class).
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    static {
        postgres.start();
        kafka.start();
    }

    @Autowired EmailPublisher publisher;
    @Autowired EmailConsumer consumer;
    @Autowired MockEmailClient client;
    @Autowired ResultStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void flakyProvider() {
        client.setFailureRate(0.3);
        consumer.setCrashAfterSendRate(0.03);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Waits until every user has an outcome AND nothing has changed for 3 seconds (all messages drained). */
    private void awaitQuiet(String run, int users) throws InterruptedException {
        String last = "";
        long quietSince = System.currentTimeMillis();
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(150).toMillis();
        while (System.currentTimeMillis() < deadline) {
            int known = count("SELECT COUNT(DISTINCT user_id) FROM email_results WHERE run_id = ?", run);
            int calls = client.callsByOutcome().values().stream().mapToInt(Integer::intValue).sum();
            String snapshot = known + "/" + calls + "/" + consumer.skippedDuplicates();
            if (!snapshot.equals(last)) {
                last = snapshot;
                quietSince = System.currentTimeMillis();
            } else if (known >= users && System.currentTimeMillis() - quietSince > 3000) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("did not drain in time, last snapshot " + last);
    }

    @Test
    void exactlyOneEmailPerUser_despiteDuplicatePublishesProviderFailuresAndCrashes() throws Exception {
        String run = "k1";

        // Two publishers at once = a ShedLock that expired, or someone hitting the manual publish: 2000 messages.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> a = pool.submit(() -> publisher.publishRun(run, 1000));
        Future<Integer> b = pool.submit(() -> publisher.publishRun(run, 1000));
        a.get();
        b.get();
        pool.shutdown();

        awaitQuiet(run, 1000);

        int sent = store.distinctUsers(run, "SENT");
        int dead = store.distinctUsers(run, "DEAD");
        assertThat(count("SELECT COUNT(DISTINCT user_id) FROM email_results WHERE run_id = ?", run))
                .isEqualTo(1000);                                      // every user ended up SENT or DEAD
        assertThat(dead).isLessThan(100);                              // ~0.3^3 give up after 3 attempts

        // exactly one delivery per user, never two
        int delivered = count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":%");
        int deliveredUsers = count("SELECT COUNT(DISTINCT user_id) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":%");
        assertThat(deliveredUsers).isEqualTo(delivered);
        assertThat(sent).isLessThanOrEqualTo(delivered);               // every SENT user was really delivered

        // the duplicate messages really were skipped, and the crash gap really happened and was absorbed
        assertThat(consumer.skippedDuplicates()).isPositive();
        int ignoredByProvider = count(
                "SELECT COUNT(*) FROM provider_calls WHERE outcome = 'DUPLICATE_IGNORED' AND idempotency_key LIKE ?", run + ":%");
        System.out.println("k1: sent=" + sent + " dead=" + dead + " skippedDuplicateMessages=" + consumer.skippedDuplicates()
                + " duplicateSendsIgnoredByProvider=" + ignoredByProvider);
    }

    @Test
    void resendOfSentUser_deliversAgainUnderANewIdempotencyKey() {
        String run = "k2";
        client.setFailureRate(0);
        consumer.setCrashAfterSendRate(0);
        publisher.publishRun(run, 5);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> store.distinctUsers(run, "SENT") == 5);

        EmailRequest r = publisher.resend(run, 3);
        assertThat(r.resendCount()).isEqualTo(1);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() ->
                count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":3:%") == 2);

        assertThat(count("SELECT COUNT(*) FROM email_results WHERE run_id = ? AND user_id = 3 AND status = 'SENT'", run)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":4:%")).isEqualTo(1);
    }

    @Test
    void staleOriginalRequest_afterResendWasSent_isSkipped() {
        String run = "k4";
        client.setFailureRate(0);
        consumer.setCrashAfterSendRate(0);
        publisher.publishRun(run, 3);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> store.distinctUsers(run, "SENT") == 3);
        publisher.resend(run, 2);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() ->
                count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":2:%") == 2);
        int skippedBefore = consumer.skippedDuplicates();

        publisher.publishRun(run, 3);   // someone re-publishes the whole run: r0 for users 1..3 arrives again
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> consumer.skippedDuplicates() >= skippedBefore + 3);

        // user 2 still has exactly 2 deliveries (r0, r1), users 1 and 3 still exactly 1
        assertThat(count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":2:%")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM provider_delivered WHERE idempotency_key LIKE ?", run + ":%")).isEqualTo(4);
    }

    @Test
    void deadLetteredUser_getsEmailAfterFix_viaResend_othersStayDead() {
        String run = "k3";
        client.setFailureRate(1.0);                // provider broken (e.g. bad API key)
        consumer.setCrashAfterSendRate(0);
        publisher.publishRun(run, 3);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> store.distinctUsers(run, "DEAD") == 3);
        assertThat(store.distinctUsers(run, "SENT")).isZero();

        client.setFailureRate(0);                  // "we fixed the API key"
        publisher.resend(run, 2);
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> store.distinctUsers(run, "SENT") == 1);

        assertThat(store.distinctUsers(run, "DEAD")).isEqualTo(2);
        assertThat(store.deadUsers(run)).containsExactly(1, 3);
    }
}
