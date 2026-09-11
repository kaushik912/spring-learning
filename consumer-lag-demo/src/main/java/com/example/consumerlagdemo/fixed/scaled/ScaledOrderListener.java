package com.example.consumerlagdemo.fixed.scaled;

import com.example.consumerlagdemo.OrderTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FIX 2: the per-message work is exactly as slow as the buggy listener's -
 * sometimes the downstream call genuinely can't be sped up any further.
 * Instead, concurrency=3 matches the topic's 3 partitions, giving 3
 * consumer threads (equivalent to 3 consumer instances in the group)
 * working in parallel. Aggregate consume rate triples, which is enough to
 * outrun the produce rate even though no single thread got any faster.
 */
@Component
public class ScaledOrderListener {

    @KafkaListener(topics = OrderTopics.SCALED_ORDERS, groupId = OrderTopics.SCALED_GROUP, concurrency = "3")
    public void onMessage(String payload) throws InterruptedException {
        Thread.sleep(30);
    }
}
