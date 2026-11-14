package com.example.emailkafka;

public final class Topics {

    /** One message per (run, user, resendCount). Key = userId, so all messages of a user hit one partition. */
    public static final String EMAIL_REQUESTS = "email-requests";

    /** Spring Kafka's default dead-letter name for a failed record is "<topic>-dlt". */
    public static final String EMAIL_REQUESTS_DLT = EMAIL_REQUESTS + "-dlt";

    public static final int PARTITIONS = 6;

    private Topics() {
    }
}
