package com.example.duplicatedeliverydemo.fixed.persistent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * The id is a separate auto-generated column, NOT the eventId itself. With
 * a manually-assigned @Id, Spring Data JPA's save() can't tell "new" from
 * "already exists" and calls merge() - a silent upsert that would let a
 * redelivered event look like a normal update instead of a rejected
 * duplicate. Keeping eventId as its own unique column means save() is
 * always a fresh insert, so the unique constraint actually has something to
 * reject.
 */
@Entity
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }
}
