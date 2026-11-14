package com.example.emailkafka;

import com.example.emailkafka.EmailKafkaApplication.DemoProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The single publisher: turns "email everyone today" into one Kafka message per user.
 * Every instance has the @Scheduled method; ShedLock lets one run per tick. Correctness does not depend on it:
 * if two publishers do run, users just get two messages and the consumer skips the second one.
 */
@Component
public class EmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmailPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ResultStore store;
    private final DemoProperties props;

    public EmailPublisher(KafkaTemplate<String, String> kafka, ResultStore store, DemoProperties props) {
        this.kafka = kafka;
        this.store = store;
        this.props = props;
    }

    static String today() {
        return LocalDate.now().toString();
    }

    @Scheduled(cron = "${demo.publish-cron}")
    @SchedulerLock(name = "email-publisher", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void scheduledPublish() {
        String runId = today();
        if (store.runPublished(runId)) {
            log.info("[{}] run {} already published, nothing to do", props.instanceId(), runId);
            return;
        }
        publishRun(runId, props.userCount());
        // marker AFTER all messages are acked by Kafka: a crash halfway means "not published" -> next tick retries
        store.markRunPublished(runId, props.instanceId());
    }

    /** Send one message per user and wait until Kafka has acknowledged every one. No marker, safe to repeat. */
    public int publishRun(String runId, int userCount) {
        List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>();
        for (int userId = 1; userId <= userCount; userId++) {
            sends.add(send(new EmailRequest(runId, userId, 0)));
        }
        sends.forEach(CompletableFuture::join); // throws if any send failed
        log.info("[{}] published run={} messages={}", props.instanceId(), runId, userCount);
        return userCount;
    }

    /** Support "resend": a new message with the next resendCount, which means a new idempotency key. */
    public EmailRequest resend(String runId, int userId) {
        EmailRequest r = new EmailRequest(runId, userId, store.nextResendCount(runId, userId));
        send(r).join();
        log.info("[{}] resend queued {}", props.instanceId(), r);
        return r;
    }

    private CompletableFuture<SendResult<String, String>> send(EmailRequest r) {
        // key = userId: the same user always lands in the same partition, so one consumer handles them in order
        return kafka.send(Topics.EMAIL_REQUESTS, String.valueOf(r.userId()), r.toValue());
    }
}
