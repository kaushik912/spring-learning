package com.example.singletonscopebugdemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
 * Fires many concurrent requests, each with its own user id, at each
 * endpoint and counts how often the response's resolvedUserId doesn't match
 * the userId that request actually sent — i.e. it saw someone else's value.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RaceConditionReproductionTest {

    private static final int CONCURRENT_REQUESTS = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void givenSharedSingletonField_whenManyConcurrentRequests_thenUserIdsGetCorrupted() throws Exception {
        long corrupted = fireConcurrentRequests("/api/buggy/orders/user-%d");

        // Reproduces the race: with 100 concurrent requests racing on one
        // shared field, at least some responses resolve to another user's id.
        assertThat(corrupted).isGreaterThan(0);
    }

    @Test
    void givenLocalVariable_whenManyConcurrentRequests_thenNoCorruption() throws Exception {
        long corrupted = fireConcurrentRequests("/api/safe/local/orders/user-%d");

        assertThat(corrupted).isZero();
    }

    @Test
    void givenThreadLocalContext_whenManyConcurrentRequests_thenNoCorruption() throws Exception {
        long corrupted = fireConcurrentRequests("/api/safe/threadlocal/orders/user-%d");

        assertThat(corrupted).isZero();
    }

    @SuppressWarnings("unchecked")
    private long fireConcurrentRequests(String pathTemplate) throws Exception {
        String baseUrl = "http://localhost:" + port;
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, CONCURRENT_REQUESTS)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        String url = baseUrl + String.format(pathTemplate, i);
                        Map<String, Object> body = restTemplate.getForObject(url, Map.class);
                        return Boolean.TRUE.equals(body.get("corrupted"));
                    })
                    .collect(Collectors.toList());

            List<Future<Boolean>> futures = executor.invokeAll(calls);
            long corrupted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    corrupted++;
                }
            }
            return corrupted;
        } finally {
            executor.shutdown();
        }
    }
}
