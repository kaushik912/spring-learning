package com.example.consumerlagdemo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces a steady, sustained stream of events — standing in for real
 * production traffic. Spreads messages round-robin across partitions
 * explicitly so throughput comparisons between scenarios aren't skewed by
 * hash-based partition assignment happening to land unevenly.
 */
@Component
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Executor executor = Executors.newCachedThreadPool();

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Fires the whole send loop on a background thread and returns immediately. */
    public CompletableFuture<Void> produceLoadAsync(String topic, int count, long intervalMillis) {
        return CompletableFuture.runAsync(() -> produceLoad(topic, count, intervalMillis), executor);
    }

    public void produceLoad(String topic, int count, long intervalMillis) {
        for (int i = 0; i < count; i++) {
            int partition = i % KafkaTopicsConfig.PARTITIONS;
            kafkaTemplate.send(new ProducerRecord<>(topic, partition, "order-" + i, "payload-" + i));
            sleep(intervalMillis);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
