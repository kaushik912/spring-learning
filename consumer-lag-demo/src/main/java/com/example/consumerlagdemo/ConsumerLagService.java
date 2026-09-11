package com.example.consumerlagdemo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Computes real consumer lag the same way monitoring tools (Burrow, Kafka
 * Manager, lag exporters) do: for each partition, the topic's current log
 * end offset minus that consumer group's last committed offset. This is
 * exactly "produce rate minus consume rate, accumulated over time" made
 * concrete — it has nothing to do with whether the consumer is alive.
 */
@Component
public class ConsumerLagService {

    private final KafkaAdmin kafkaAdmin;

    public ConsumerLagService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public long getTotalLag(String groupId, String topic) {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription description = admin.describeTopics(Set.of(topic)).topicNameValues().get(topic).get();

            Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
            description.partitions().forEach(partitionInfo -> {
                TopicPartition tp = new TopicPartition(topic, partitionInfo.partition());
                latestRequest.put(tp, OffsetSpec.latest());
            });

            Map<TopicPartition, ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(latestRequest).all().get();

            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();

            return endOffsets.entrySet().stream()
                    .mapToLong(entry -> {
                        long endOffset = entry.getValue().offset();
                        long committedOffset = committed.containsKey(entry.getKey())
                                ? committed.get(entry.getKey()).offset()
                                : 0L;
                        return Math.max(0, endOffset - committedOffset);
                    })
                    .sum();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute consumer lag for group " + groupId, e);
        }
    }

    public Map<Integer, Long> getPerPartitionLag(String groupId, String topic) {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription description = admin.describeTopics(Set.of(topic)).topicNameValues().get(topic).get();

            Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
            description.partitions().forEach(partitionInfo -> {
                TopicPartition tp = new TopicPartition(topic, partitionInfo.partition());
                latestRequest.put(tp, OffsetSpec.latest());
            });

            Map<TopicPartition, ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(latestRequest).all().get();
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();

            return endOffsets.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().partition(),
                            entry -> {
                                long endOffset = entry.getValue().offset();
                                long committedOffset = committed.containsKey(entry.getKey())
                                        ? committed.get(entry.getKey()).offset()
                                        : 0L;
                                return Math.max(0, endOffset - committedOffset);
                            }));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute consumer lag for group " + groupId, e);
        }
    }
}
