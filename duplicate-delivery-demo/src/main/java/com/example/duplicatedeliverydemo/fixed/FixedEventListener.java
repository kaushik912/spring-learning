package com.example.duplicatedeliverydemo.fixed;

import com.example.duplicatedeliverydemo.EmailService;
import com.example.duplicatedeliverydemo.EventTopics;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * THE FIX: track which eventIds have already been handled, and skip the
 * side effect for anything seen before. The listener still gets called
 * twice for the same record (redelivery is still happening - this doesn't
 * "fix" Kafka's delivery guarantee, and shouldn't try to), but the second
 * call is a no-op instead of a second email.
 *
 * A ConcurrentHashMap-backed Set is enough here because a single-partition
 * topic with one consumer never processes two deliveries of the same
 * record concurrently - the second delivery only happens after the first
 * one fails and is retried. A store shared across consumer instances
 * (Redis, a DB unique constraint) is needed once dedup has to survive a
 * process restart or work across multiple consumer instances - see
 * idempotency-key-race-demo for that shape of fix.
 */
@Component
public class FixedEventListener {

    private final EmailService emailService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public FixedEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = EventTopics.FIXED_EVENTS, groupId = EventTopics.FIXED_GROUP)
    public void onMessage(String eventId) {
        if (!processedEventIds.add(eventId)) {
            return; // already handled this exact event - skip silently
        }
        emailService.sendConfirmation(eventId);
        // Same simulated post-side-effect failure as the buggy listener,
        // still triggering a redelivery - the dedup check above is what
        // makes that redelivery harmless instead of a duplicate email.
        throw new RuntimeException("simulated downstream failure after side effect for event " + eventId);
    }
}
