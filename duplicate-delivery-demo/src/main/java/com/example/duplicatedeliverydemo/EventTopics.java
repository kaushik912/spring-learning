package com.example.duplicatedeliverydemo;

public final class EventTopics {

    public static final String BUGGY_EVENTS = "buggy-events";
    public static final String FIXED_EVENTS = "fixed-events";
    public static final String PERSISTENT_EVENTS = "persistent-events";

    public static final String BUGGY_GROUP = "buggy-event-processor";
    public static final String FIXED_GROUP = "fixed-event-processor";
    public static final String PERSISTENT_GROUP = "persistent-event-processor";

    private EventTopics() {
    }
}
