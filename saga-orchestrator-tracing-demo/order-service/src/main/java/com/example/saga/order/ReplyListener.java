package com.example.saga.order;

import com.example.saga.common.Reply;
import com.example.saga.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReplyListener {

    private final SagaOrchestrator orchestrator;

    public ReplyListener(SagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @KafkaListener(topics = Topics.ORDER_REPLIES)
    void onReply(Reply reply) {
        orchestrator.onReply(reply);
    }
}
