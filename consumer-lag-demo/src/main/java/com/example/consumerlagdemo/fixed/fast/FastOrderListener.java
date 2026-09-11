package com.example.consumerlagdemo.fixed.fast;

import com.example.consumerlagdemo.OrderTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FIX 1: same single consumer thread, but the per-message work was sped up
 * (e.g. an async/non-blocking downstream client, batching, caching, or
 * simply removing unnecessary work). Consume rate now comfortably exceeds
 * the produce rate, so lag stays near zero instead of climbing.
 */
@Component
public class FastOrderListener {

    @KafkaListener(topics = OrderTopics.FAST_ORDERS, groupId = OrderTopics.FAST_GROUP, concurrency = "1")
    public void onMessage(String payload) throws InterruptedException {
        Thread.sleep(3);
    }
}
