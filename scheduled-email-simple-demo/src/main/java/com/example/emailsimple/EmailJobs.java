package com.example.emailsimple;

import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The two scheduled entry points. Every instance has them; ShedLock lets one instance run each per tick. */
@Component
public class EmailJobs {

    private final EmailService service;

    public EmailJobs(EmailService service) {
        this.service = service;
    }

    static String today() {
        return LocalDate.now().toString();
    }

    /** The main job (real life: daily). runId = the day, so re-running within the day is a no-op. */
    @Scheduled(cron = "${demo.send-cron}")
    @SchedulerLock(name = "email-send", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void send() {
        service.seed(today());
        service.sendPending();
    }

    /** Retries FAILED rows (any day) up to max-attempts. Also picks up support "resend" requests. */
    @Scheduled(cron = "${demo.sweep-cron}")
    @SchedulerLock(name = "email-sweeper", lockAtMostFor = "PT2M", lockAtLeastFor = "PT3S")
    public void sweep() {
        service.retryFailed();
    }
}
