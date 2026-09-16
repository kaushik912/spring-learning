package com.example.duplicatedeliverydemo.fixed.persistent;

import com.example.duplicatedeliverydemo.EmailService;
import com.example.duplicatedeliverydemo.EventTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Same shape as FixedEventListener, but the dedup check goes through
 * ProcessedEventStore (a DB row) instead of a JVM-local Set - so the
 * "already handled this eventId" fact is still known after a restart, not
 * just for the lifetime of this process.
 */
@Component
public class PersistentEventListener {

    private final EmailService emailService;
    private final ProcessedEventStore processedEventStore;

    public PersistentEventListener(EmailService emailService, ProcessedEventStore processedEventStore) {
        this.emailService = emailService;
        this.processedEventStore = processedEventStore;
    }

    @KafkaListener(topics = EventTopics.PERSISTENT_EVENTS, groupId = EventTopics.PERSISTENT_GROUP)
    public void onMessage(String eventId) {
        if (!processedEventStore.tryClaim(eventId)) {
            return; // already handled this exact event - skip silently
        }
        emailService.sendConfirmation(eventId);
        // Same simulated post-side-effect failure as the other listeners,
        // still triggering a redelivery - the persistent claim above is
        // what makes that redelivery harmless even across a restart.
        throw new RuntimeException("simulated downstream failure after side effect for event " + eventId);
    }
}
