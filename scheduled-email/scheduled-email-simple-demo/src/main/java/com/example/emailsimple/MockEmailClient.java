package com.example.emailsimple;

import com.example.emailsimple.EmailSimpleApplication.DemoProperties;
import com.example.emailsimple.ProviderCall.Outcome;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Fake email API. Randomly fails (503). Like real providers it honours an idempotency key:
 * same key = delivered once, repeats are acknowledged but ignored.
 */
@Component
public class MockEmailClient {

    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String msg) {
            super(msg);
        }
    }

    private final ProviderDeliveryRepository deliveries;
    private final ProviderCallRepository calls;
    private volatile double failureRate;

    public MockEmailClient(ProviderDeliveryRepository deliveries, ProviderCallRepository calls, DemoProperties props) {
        this.deliveries = deliveries;
        this.calls = calls;
        this.failureRate = props.providerFailureRate();
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate; // tests use this to "fix the provider"
    }

    public void send(String idempotencyKey, int userId) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            calls.save(new ProviderCall(idempotencyKey, Outcome.TRANSIENT_ERROR));
            throw new EmailSendException("503 from provider");
        }
        Outcome outcome = Outcome.DUPLICATE_IGNORED;
        if (!deliveries.existsById(idempotencyKey)) {
            try {
                deliveries.saveAndFlush(new ProviderDelivery(idempotencyKey, userId));
                outcome = Outcome.DELIVERED;
            } catch (DataIntegrityViolationException sameKeyArrivedAtTheSameTime) {
                // the primary key on idempotency_key rejected it: someone else delivered it first
            }
        }
        calls.save(new ProviderCall(idempotencyKey, outcome));
    }

    public long deliveredCount() {
        return deliveries.count();
    }

    public Map<String, Long> callsByOutcome() {
        Map<String, Long> out = new TreeMap<>();
        for (Outcome o : Outcome.values()) {
            long n = calls.countByOutcome(o);
            if (n > 0) {
                out.put(o.name(), n);
            }
        }
        return out;
    }
}
