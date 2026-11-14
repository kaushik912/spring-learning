package com.example.emaildedupdemo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The single publisher. Every instance has this @Scheduled method, but ShedLock lets only one run per tick
 * (layer 1: avoid wasted duplicate work). Correctness does NOT depend on it: runId is derived from the
 * schedule slot and enqueue() is idempotent (layer 2).
 */
@Component
public class EmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmailPublisher.class);
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final OutboxRepository outbox;
    private final DemoProperties props;

    public EmailPublisher(OutboxRepository outbox, DemoProperties props) {
        this.outbox = outbox;
        this.props = props;
    }

    @Scheduled(cron = "${demo.publish-cron}")
    @SchedulerLock(name = "email-publisher", lockAtMostFor = "PT1M", lockAtLeastFor = "PT20S")
    public void scheduledPublish() {
        publish(currentRunId());
    }

    /** Enqueue one email job per user for this run. Safe to call from many threads/instances. */
    public int publish(String runId) {
        int inserted = 0;
        for (int userId = 1; userId <= props.userCount(); userId++) {
            if (outbox.enqueue(runId, userId)) {
                inserted++;
            }
        }
        log.info("[{}] publisher run={} enqueued={} (skipped as already-enqueued={})",
                props.instanceId(), runId, inserted, props.userCount() - inserted);
        return inserted;
    }

    static String currentRunId() {
        return LocalDateTime.now().format(SLOT);
    }
}
