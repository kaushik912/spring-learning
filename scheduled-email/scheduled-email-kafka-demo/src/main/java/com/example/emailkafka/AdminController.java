package com.example.emailkafka;

import com.example.emailkafka.EmailKafkaApplication.DemoProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Demo/support endpoints. NO AUTH here; protect these in a real app. */
@RestController
public class AdminController {

    private final EmailPublisher publisher;
    private final ResultStore store;
    private final MockEmailClient client;
    private final DemoProperties props;

    public AdminController(EmailPublisher publisher, ResultStore store, MockEmailClient client, DemoProperties props) {
        this.publisher = publisher;
        this.store = store;
        this.client = client;
        this.props = props;
    }

    @GetMapping("/admin/emails/report")
    public Map<String, Object> report(@RequestParam(required = false) String runId) {
        String run = runId != null ? runId : EmailPublisher.today();
        int sent = store.distinctUsers(run, "SENT");
        int dead = store.distinctUsers(run, "DEAD");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", run);
        r.put("runPublished", store.runPublished(run));
        r.put("usersSent", sent);
        r.put("usersDead", dead);
        r.put("completed", sent + dead >= props.userCount());
        r.put("sentByInstance", store.sentByInstance(run));
        r.put("deadUsers(first 20)", store.deadUsers(run));
        r.put("providerUniqueDeliveries", client.deliveredCount());
        r.put("providerCallsByOutcome", client.callsByOutcome());
        return r;
    }

    /** Publish a run by hand WITHOUT the ShedLock or the "already published" marker: proves duplicates are harmless. */
    @PostMapping("/admin/emails/{runId}/publish")
    public Map<String, Object> publish(@PathVariable String runId) {
        return Map.of("runId", runId, "messages", publisher.publishRun(runId, props.userCount()));
    }

    /** Customer says "I never got it": send a new message (new idempotency key) for that user. */
    @PostMapping("/admin/emails/{runId}/{userId}/resend")
    public Map<String, Object> resend(@PathVariable String runId, @PathVariable int userId) {
        EmailRequest r = publisher.resend(runId, userId);
        return Map.of("queued", r.toString(), "idempotencyKey", r.idempotencyKey());
    }
}
