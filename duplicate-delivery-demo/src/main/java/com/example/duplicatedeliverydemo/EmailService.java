package com.example.duplicatedeliverydemo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Stands in for whatever side effect a real consumer performs that must
 * NOT run twice for the same event — sending a confirmation email,
 * charging a card, incrementing a ledger. Records every call instead of
 * actually sending anything, so tests can assert exactly how many times
 * the side effect ran for a given event.
 */
@Component
public class EmailService {

    private final List<String> sentForEventIds = new CopyOnWriteArrayList<>();

    public void sendConfirmation(String eventId) {
        sentForEventIds.add(eventId);
    }

    public long countFor(String eventId) {
        return sentForEventIds.stream().filter(eventId::equals).count();
    }
}
