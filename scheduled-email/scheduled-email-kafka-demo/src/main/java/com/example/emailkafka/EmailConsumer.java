package com.example.emailkafka;

import com.example.emailkafka.EmailKafkaApplication.DemoProperties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The workers. All instances join one consumer group, Kafka gives each a share of the partitions.
 * If a listener call throws, the error handler (KafkaConfig) retries and finally dead-letters the record.
 * If an instance dies, its uncommitted records are redelivered to another instance (that replaces a "reaper").
 */
@Component
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    private final ResultStore store;
    private final MockEmailClient client;
    private final DemoProperties props;
    private final AtomicInteger skippedDuplicates = new AtomicInteger();
    private volatile double crashAfterSendRate;

    public EmailConsumer(ResultStore store, MockEmailClient client, DemoProperties props) {
        this.store = store;
        this.client = client;
        this.props = props;
        this.crashAfterSendRate = props.crashAfterSendRate();
    }

    public void setCrashAfterSendRate(double rate) {
        this.crashAfterSendRate = rate; // tests switch the simulated crash off
    }

    /** How many already-sent requests this instance skipped (duplicate publish / redelivery). */
    public int skippedDuplicates() {
        return skippedDuplicates.get();
    }

    @KafkaListener(topics = Topics.EMAIL_REQUESTS)
    public void onRequest(String value) {
        EmailRequest req = EmailRequest.parse(value);

        // 1) duplicate? (published twice, or redelivered after a crash/rebalance)
        if (store.alreadySent(req)) {
            skippedDuplicates.incrementAndGet();
            return;
        }
        // 2) send. A provider failure throws -> error handler retries, then dead-letters
        client.send(req.idempotencyKey(), req.userId());

        // 3) simulated crash in the dual-write gap: email accepted, but we never record it.
        //    Kafka redelivers; the provider ignores the repeat (same idempotency key).
        if (ThreadLocalRandom.current().nextDouble() < crashAfterSendRate) {
            throw new IllegalStateException("simulated crash after send for " + req);
        }
        // 4) record. Returning normally lets Spring Kafka commit the offset.
        store.markSent(req, props.instanceId());
    }

    /** Records that exhausted their retries land here (published by the error handler's recoverer). */
    @KafkaListener(topics = Topics.EMAIL_REQUESTS_DLT, groupId = "email-dead-letters")
    public void onDeadLetter(String value) {
        EmailRequest req = EmailRequest.parse(value);
        store.markDead(req, props.instanceId());
        log.warn("[{}] dead-lettered {}", props.instanceId(), req);
    }
}
