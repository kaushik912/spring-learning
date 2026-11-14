package com.example.emaildedupdemo;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final EmailPublisher publisher;
    private final OutboxRepository outbox;
    private final MockEmailProvider provider;
    private final DemoProperties props;

    public ReportController(EmailPublisher publisher, OutboxRepository outbox, MockEmailProvider provider,
                            DemoProperties props) {
        this.publisher = publisher;
        this.outbox = outbox;
        this.provider = provider;
        this.props = props;
    }

    /** Manual publish with NO ShedLock: proves the unique constraint alone stops duplicates. */
    @PostMapping("/api/publish")
    public Map<String, Object> publish(@RequestParam(required = false) String runId) {
        String run = runId != null ? runId : EmailPublisher.currentRunId();
        int inserted = publisher.publish(run);
        return Map.of("runId", run, "enqueued", inserted);
    }

    @GetMapping("/api/report")
    public Map<String, Object> report(@RequestParam(required = false) String runId) {
        String run = runId != null ? runId : EmailPublisher.currentRunId();
        int delivered = provider.deliveredCount(run);
        Map<String, Integer> calls = provider.callsByOutcome(run);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", run);
        r.put("expectedUsers", props.userCount());
        r.put("outboxByStatus", outbox.countByStatus(run));
        r.put("sentByInstance", outbox.sentByInstance(run));
        r.put("providerDeliveredUnique", delivered);
        r.put("providerCallsByOutcome", calls);
        r.put("maxAttemptsForAnyUser", outbox.maxAttempts(run));
        // delivered is a PK-unique set, so >1 per user is impossible at the provider; check none missing.
        r.put("exactlyOnceForAllUsers", delivered == props.userCount()
                && outbox.countByStatus(run).getOrDefault("SENT", 0) == props.userCount());
        return r;
    }
}
