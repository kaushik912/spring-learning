package com.example.emailkafka;

/**
 * The Kafka message: "send this user an email for this run". Travels as the plain string
 * "runId,userId,resendCount" to keep serializer config out of the way.
 */
public record EmailRequest(String runId, int userId, int resendCount) {

    public String toValue() {
        return runId + "," + userId + "," + resendCount;
    }

    public static EmailRequest parse(String value) {
        String[] p = value.split(",");
        return new EmailRequest(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    /** Same on every retry/redelivery (provider dedups); changes only on a deliberate support resend. */
    public String idempotencyKey() {
        return runId + ":" + userId + ":r" + resendCount;
    }
}
