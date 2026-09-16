package com.example.duplicatedeliverydemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.duplicatedeliverydemo.fixed.persistent.ProcessedEventRepository;
import com.example.duplicatedeliverydemo.fixed.persistent.ProcessedEventStore;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Proves the exact claim from the prompt: Kafka's at-least-once delivery
 * means the same record CAN reach a consumer twice (here via a simulated
 * post-side-effect failure that triggers a container redelivery - a
 * consumer crash-before-commit or a group rebalance mid-processing cause
 * the identical redelivery, just not deterministically on demand). A
 * consumer that doesn't dedup runs its side effect twice; one that tracks
 * processed eventIds runs it exactly once - whether that tracking lives in
 * JVM memory (fine until a restart) or a database row (survives one).
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {EventTopics.BUGGY_EVENTS, EventTopics.FIXED_EVENTS, EventTopics.PERSISTENT_EVENTS})
class DuplicateDeliveryReproductionTest {

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ProcessedEventStore processedEventStore;

    @Test
    void givenNonIdempotentListener_whenKafkaRedeliversSameRecord_thenSideEffectRunsTwice() throws Exception {
        String eventId = eventProducer.publish(EventTopics.BUGGY_EVENTS);

        awaitTrue(() -> emailService.countFor(eventId) >= 2, 5000);

        // Same eventId, same record redelivered by the container after the
        // simulated failure - and nothing stopped the email from going out
        // a second time.
        assertThat(emailService.countFor(eventId)).isEqualTo(2);
    }

    @Test
    void givenIdempotentListener_whenKafkaRedeliversSameRecord_thenSideEffectRunsOnce() throws Exception {
        String eventId = eventProducer.publish(EventTopics.FIXED_EVENTS);

        // Give both delivery attempts (original + retry) time to finish;
        // there's no "it ran twice" signal to poll for here - the whole
        // point is the count stays at 1.
        Thread.sleep(2000);

        assertThat(emailService.countFor(eventId)).isEqualTo(1);
    }

    @Test
    void givenPersistentListener_whenKafkaRedeliversSameRecord_thenSideEffectRunsOnce() throws Exception {
        String eventId = eventProducer.publish(EventTopics.PERSISTENT_EVENTS);

        // Same redelivery mechanism as the other two listeners - proves the
        // DB-backed store dedups a normal same-process redelivery too, not
        // just the restart case covered by the test below.
        Thread.sleep(2000);

        assertThat(emailService.countFor(eventId)).isEqualTo(1);
    }

    @Test
    void givenEventAlreadyClaimedInStore_whenFreshStoreInstanceChecksIt_thenClaimIsRejected() {
        // Claim directly through the store - simulating a previous process
        // instance having already handled this eventId, no Kafka timing
        // involved.
        String eventId = "already-processed-event";
        boolean firstClaim = processedEventStore.tryClaim(eventId);
        assertThat(firstClaim).isTrue();

        // A brand-new instance, sharing no JVM state whatsoever with the
        // Spring-managed ProcessedEventStore singleton above - the closest
        // a same-JVM test can get to proving "this claim would still be
        // known after a restart" without literally killing the process. If
        // the claim lived in a JVM field like FixedEventListener's Set,
        // this fresh instance would have no way to know about it.
        ProcessedEventStore freshStore = new ProcessedEventStore(processedEventRepository);

        boolean claimedAgain = freshStore.tryClaim(eventId);

        assertThat(claimedAgain).isFalse();
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
