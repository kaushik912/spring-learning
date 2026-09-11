package com.example.consumerlagdemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Sustains a steady produce rate against each scenario's topic and samples
 * real consumer lag (log end offset minus committed offset) while the
 * consumer runs — no exceptions, no restarts, just a rate mismatch. Proves
 * the exact claim from the prompt: a "healthy" consumer that's too slow (or
 * too few) still shows ever-increasing lag, and it takes either faster
 * processing or more consumers/partitions to fix, not a health check.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@EmbeddedKafka(
        partitions = KafkaTopicsConfig.PARTITIONS,
        topics = {OrderTopics.BUGGY_ORDERS, OrderTopics.FAST_ORDERS, OrderTopics.SCALED_ORDERS})
class ConsumerLagReproductionTest {

    private static final int MESSAGE_COUNT = 150;
    private static final long INTERVAL_MILLIS = 15; // produce rate ~= 66 msgs/sec
    private static final long PRODUCE_DURATION_MILLIS = MESSAGE_COUNT * INTERVAL_MILLIS;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void givenSlowSingleThreadedConsumer_whenLoadIsSustained_thenLagClimbsDespiteNoErrors() throws Exception {
        startLoad(OrderTopics.BUGGY_ORDERS);

        long earlyLag = waitThenGetLag(OrderTopics.BUGGY_GROUP, OrderTopics.BUGGY_ORDERS, 800);
        long lagAtProductionEnd = waitThenGetLag(
                OrderTopics.BUGGY_GROUP, OrderTopics.BUGGY_ORDERS, PRODUCE_DURATION_MILLIS - 800 + 300);

        // "Healthy" consumer, zero errors thrown - and the backlog still grew
        // the entire time messages kept arriving, purely from the rate mismatch.
        assertThat(lagAtProductionEnd).isGreaterThan(earlyLag);
        assertThat(lagAtProductionEnd).isGreaterThan(30);
    }

    @Test
    void givenSpeededUpProcessing_whenLoadIsSustained_thenLagStaysNearZero() throws Exception {
        startLoad(OrderTopics.FAST_ORDERS);

        long lagAtProductionEnd =
                waitThenGetLag(OrderTopics.FAST_GROUP, OrderTopics.FAST_ORDERS, PRODUCE_DURATION_MILLIS + 500);

        // Same produce rate as the buggy scenario - but consume rate now
        // comfortably exceeds it, so lag never has a chance to build up.
        assertThat(lagAtProductionEnd).isLessThanOrEqualTo(20);
    }

    @Test
    void givenMoreConsumerThreadsAndPartitions_whenLoadIsSustained_thenLagStaysBounded() throws Exception {
        startLoad(OrderTopics.SCALED_ORDERS);

        long lagAtProductionEnd = waitThenGetLag(
                OrderTopics.SCALED_GROUP, OrderTopics.SCALED_ORDERS, PRODUCE_DURATION_MILLIS + 500);

        // Same per-message processing time as the buggy scenario (30ms) -
        // but 3 partitions + 3 consumer threads triple aggregate throughput.
        assertThat(lagAtProductionEnd).isLessThanOrEqualTo(30);
    }

    private void startLoad(String topic) {
        restTemplate.postForEntity(
                url("/api/load/" + topic + "?count=" + MESSAGE_COUNT + "&intervalMillis=" + INTERVAL_MILLIS),
                null, Map.class);
    }

    @SuppressWarnings("unchecked")
    private long waitThenGetLag(String groupId, String topic, long waitMillis) throws InterruptedException {
        Thread.sleep(waitMillis);
        Map<String, Object> body = restTemplate.getForObject(url("/api/lag/" + groupId + "/" + topic), Map.class);
        return ((Number) body.get("totalLag")).longValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
