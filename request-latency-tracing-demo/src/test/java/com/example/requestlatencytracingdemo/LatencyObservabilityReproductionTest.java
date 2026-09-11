package com.example.requestlatencytracingdemo;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Proves the exact claim from the prompt: two endpoints with identical
 * real latency (same Thread.sleep durations), one you can diagnose and one
 * you can't - purely because of what's instrumented, not what's slow.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LatencyObservabilityReproductionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void givenBuggyEndpoint_whenCalled_thenNoPerStepTimingIsRecorded() {
        long pricingCountBefore = pricingTimerCount();

        restTemplate.getForEntity(url("/api/buggy/orders/42"), String.class);

        long pricingCountAfter = pricingTimerCount();

        // The slow call happened (the response took ~470ms) but nothing
        // recorded it as its own observation - "order.pricing" never moves.
        assertThat(pricingCountAfter).isEqualTo(pricingCountBefore);
    }

    @Test
    void givenFixedEndpoint_whenCalled_thenPerStepTimingRevealsWhichStepDominates() {
        restTemplate.getForEntity(url("/api/fixed/orders/42"), String.class);

        double validateMs = meterRegistry.get("order.validate").timer().totalTime(TimeUnit.MILLISECONDS);
        double fetchMs = meterRegistry.get("order.fetch").timer().totalTime(TimeUnit.MILLISECONDS);
        double pricingMs = meterRegistry.get("order.pricing").timer().totalTime(TimeUnit.MILLISECONDS);
        double assembleMs = meterRegistry.get("order.assemble").timer().totalTime(TimeUnit.MILLISECONDS);

        // Now it's directly measurable, not guessed: pricing alone accounts
        // for more time than every other step in the request combined.
        assertThat(pricingMs).isGreaterThan(validateMs + fetchMs + assembleMs);
    }

    private long pricingTimerCount() {
        io.micrometer.core.instrument.Timer timer = meterRegistry.find("order.pricing").timer();
        return timer == null ? 0L : (long) timer.count();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
