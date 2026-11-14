package com.example.emaildedupdemo;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * N worker threads per instance, all instances compete on the same outbox. Flow per job:
 * claim (atomic) -> call provider with idempotency key -> mark SENT.
 * Crash between "provider accepted" and "mark SENT" leaves the row CLAIMED; the reaper re-queues it and the
 * retry is absorbed by the provider's idempotency key.
 */
@Component
public class EmailWorkers {

    private static final Logger log = LoggerFactory.getLogger(EmailWorkers.class);

    private final OutboxRepository outbox;
    private final MockEmailProvider provider;
    private final DemoProperties props;
    private volatile boolean running = true;
    private final List<Thread> threads = new java.util.ArrayList<>();

    public EmailWorkers(OutboxRepository outbox, MockEmailProvider provider, DemoProperties props) {
        this.outbox = outbox;
        this.provider = provider;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        for (int i = 1; i <= props.workers(); i++) {
            String workerId = props.instanceId() + "/worker-" + i;
            Thread t = new Thread(() -> loop(workerId), workerId);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        log.info("[{}] started {} workers", props.instanceId(), props.workers());
    }

    @PreDestroy
    void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
    }

    private void loop(String workerId) {
        while (running) {
            try {
                List<OutboxRepository.Job> batch = outbox.claimBatch(workerId, props.batchSize());
                if (batch.isEmpty()) {
                    Thread.sleep(300);
                    continue;
                }
                for (OutboxRepository.Job job : batch) {
                    process(workerId, job);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[{}] loop error: {}", workerId, e.toString());
            }
        }
    }

    private void process(String workerId, OutboxRepository.Job job) {
        try {
            provider.send(job.idempotencyKey(), job.runId(), job.userId());
            if (ThreadLocalRandom.current().nextDouble() < props.crashAfterSendRate()) {
                // Simulated process death in the dual-write gap: email is out, DB not updated.
                log.warn("[{}] SIMULATED CRASH after send, user={} (row stays CLAIMED)", workerId, job.userId());
                return;
            }
            outbox.markSent(job.id(), workerId);
        } catch (MockEmailProvider.TransientProviderException e) {
            outbox.release(job.id(), workerId); // retry later
        }
    }

    /** Layer 4: reaper. Single-run via ShedLock, but harmless if it ran twice. */
    @Scheduled(fixedDelayString = "${demo.reaper-interval-ms}")
    @SchedulerLock(name = "outbox-reaper", lockAtMostFor = "PT30S")
    public void reap() {
        int n = outbox.reapStale(props.claimTimeoutMs());
        if (n > 0) {
            log.info("[{}] reaper re-queued {} stale CLAIMED rows", props.instanceId(), n);
        }
    }
}
