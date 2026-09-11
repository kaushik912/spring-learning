package com.example.sagatransferdemo.saga;

import com.example.sagatransferdemo.accounts.sender.SenderAccountStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Represents sender-service's side of the saga: it only ever runs its own local transactions. */
@Component
public class SenderSagaListener {

    private final SenderAccountStore senderAccountStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SenderSagaListener(SenderAccountStore senderAccountStore, KafkaTemplate<String, Object> kafkaTemplate) {
        this.senderAccountStore = senderAccountStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.DEBIT_REQUESTED, groupId = "sender-service")
    public void onDebitRequested(TransferCommand command) {
        boolean success = senderAccountStore.debit(command.accountId(), command.amount());
        String reason = success ? null : "insufficient funds";
        kafkaTemplate.send(SagaTopics.DEBIT_RESULT, command.sagaId(), new StepResult(command.sagaId(), success, reason));
    }

    @KafkaListener(topics = SagaTopics.REFUND_REQUESTED, groupId = "sender-service")
    public void onRefundRequested(TransferCommand command) {
        senderAccountStore.refund(command.accountId(), command.amount());
        kafkaTemplate.send(SagaTopics.REFUND_RESULT, command.sagaId(), new StepResult(command.sagaId(), true, null));
    }
}
