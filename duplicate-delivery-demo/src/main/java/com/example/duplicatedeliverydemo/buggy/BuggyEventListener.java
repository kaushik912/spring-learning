package com.example.duplicatedeliverydemo.buggy;

import com.example.duplicatedeliverydemo.EmailService;
import com.example.duplicatedeliverydemo.EventTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * THE BUG: performs the side effect first, then does something else that
 * can fail (here, simulated deterministically — in real life it might be a
 * flaky downstream call, or the process crashing before the offset
 * commits). Kafka's at-least-once contract means the broker will redeliver
 * this exact record once the failure is observed — and this listener has
 * no memory of "I already sent the email for this eventId," so it just
 * does it again.
 */
@Component
public class BuggyEventListener {

    private final EmailService emailService;

    public BuggyEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = EventTopics.BUGGY_EVENTS, groupId = EventTopics.BUGGY_GROUP)
    public void onMessage(String eventId) {
        emailService.sendConfirmation(eventId);
        // Simulates a failure that happens after the side effect but before
        // the offset commits - e.g. an audit-log write, or the process
        // dying mid-poll. Either way, Kafka has no idea the email already
        // went out, and will hand this same record back to the group.
        throw new RuntimeException("simulated downstream failure after side effect for event " + eventId);
    }
}
