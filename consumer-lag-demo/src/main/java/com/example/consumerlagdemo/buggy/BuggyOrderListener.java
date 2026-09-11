package com.example.consumerlagdemo.buggy;

import com.example.consumerlagdemo.OrderTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * THE BUG: a single consumer thread doing a slow per-message call (e.g. a
 * downstream API, heavy computation). It never crashes, never throws, never
 * restarts — by every health check it's "up." But its consume rate is
 * simply lower than the produce rate, so lag climbs the entire time
 * messages keep arriving, and nothing in this listener will ever tell you
 * that's happening.
 */
@Component
public class BuggyOrderListener {

    @KafkaListener(topics = OrderTopics.BUGGY_ORDERS, groupId = OrderTopics.BUGGY_GROUP, concurrency = "1")
    public void onMessage(String payload) throws InterruptedException {
        // Simulates a slow downstream call - a synchronous HTTP call, a
        // heavy DB write, unbatched work - whatever it is, it's the
        // bottleneck, and it looks completely healthy while it runs.
        Thread.sleep(30);
    }
}
