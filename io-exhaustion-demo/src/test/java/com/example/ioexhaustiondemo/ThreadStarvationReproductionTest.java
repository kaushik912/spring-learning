package com.example.ioexhaustiondemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Boots a real embedded Tomcat with a deliberately tiny thread pool so the
 * buggy endpoint's blocking downstream call visibly serializes concurrent
 * requests, while the fixed endpoint (offloaded to its own executor) doesn't.
 * More runner-jitter-sensitive than the rest of the suite, so it's excluded
 * from the normal `mvn test` run - see the thread-starvation-demo profile.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"server.tomcat.threads.max=2", "server.tomcat.accept-count=10"})
@AutoConfigureTestRestTemplate
class ThreadStarvationReproductionTest {

    private static final int CONCURRENCY = 6;
    private static final long DELAY_MS = 200;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void givenSameConcurrencyAndConstrainedTomcatPool_whenComparingBuggyVsFixed_thenBuggySerializesAndFixedIsFaster() throws Exception {
        // Given: a 2-thread Tomcat pool and 6 callers, each triggering a 200ms downstream call

        // When: same concurrency/delay hit both endpoints
        long buggyElapsedMs = fireConcurrentRequests("/api/buggy/inventory/check", CONCURRENCY, DELAY_MS);
        long fixedElapsedMs = fireConcurrentRequests("/api/fixed/inventory/check", CONCURRENCY, DELAY_MS);

        // Then: buggy serializes behind the 2-thread Tomcat pool (>= 3 batches of 200ms);
        // fixed offloads the blocking call to its own 20-thread executor and finishes much faster
        assertThat(buggyElapsedMs).isGreaterThanOrEqualTo(DELAY_MS * 3 - 100);
        assertThat(fixedElapsedMs).isLessThan(buggyElapsedMs / 2);
    }

    private long fireConcurrentRequests(String path, int concurrency, long delayMs) throws Exception {
        ExecutorService clientPool = Executors.newFixedThreadPool(concurrency);
        try {
            String url = "http://localhost:" + port + path + "?delayMs=" + delayMs;
            List<Callable<Map<String, Object>>> calls = List.copyOf(Collections.nCopies(concurrency,
                    (Callable<Map<String, Object>>) () -> restTemplate.getForEntity(url, Map.class).getBody()));

            long start = System.nanoTime();
            List<Future<Map<String, Object>>> futures = clientPool.invokeAll(calls);
            for (Future<Map<String, Object>> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).containsEntry("available", true);
            }
            return (System.nanoTime() - start) / 1_000_000;
        } finally {
            clientPool.shutdown();
        }
    }
}
