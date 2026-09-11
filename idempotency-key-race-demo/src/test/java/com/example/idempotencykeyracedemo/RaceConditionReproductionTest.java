package com.example.idempotencykeyracedemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.idempotencykeyracedemo.charge.ChargeSimulator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Fires many concurrent retries of the SAME idempotency key — exactly what
 * happens when a client times out and retries a payment request — and
 * checks how many times the customer actually got charged.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RaceConditionReproductionTest {

    private static final int CONCURRENT_RETRIES = 50;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChargeSimulator chargeSimulator;

    @Test
    void givenCheckThenActIdempotencyCheck_whenSameKeyRetriedConcurrently_thenCustomerChargedMultipleTimes()
            throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        fireConcurrentRetries("/api/buggy/payments/" + idempotencyKey);

        // Reproduces the bug: several of the 50 concurrent retries all pass
        // the "not seen yet" check before any of them finishes recording it.
        assertThat(chargeSimulator.getChargeCount(idempotencyKey)).isGreaterThan(1);
    }

    @Test
    void givenDbUniqueConstraintClaim_whenSameKeyRetriedConcurrently_thenCustomerChargedOnce() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        fireConcurrentRetries("/api/fixed/db/payments/" + idempotencyKey);

        assertThat(chargeSimulator.getChargeCount(idempotencyKey)).isEqualTo(1);
    }

    @Test
    void givenRedisSetIfAbsentClaim_whenSameKeyRetriedConcurrently_thenCustomerChargedOnce() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        fireConcurrentRetries("/api/fixed/redis/payments/" + idempotencyKey);

        assertThat(chargeSimulator.getChargeCount(idempotencyKey)).isEqualTo(1);
    }

    private void fireConcurrentRetries(String path) throws Exception {
        String url = "http://localhost:" + port + path;
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_RETRIES);
        try {
            List<Future<?>> futures = IntStream.range(0, CONCURRENT_RETRIES)
                    .mapToObj(i -> executor.submit(() -> {
                        try {
                            startLine.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        restTemplate.postForObject(url, null, Map.class);
                    }))
                    .collect(Collectors.toList());

            startLine.countDown(); // release all threads at once
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
    }
}
