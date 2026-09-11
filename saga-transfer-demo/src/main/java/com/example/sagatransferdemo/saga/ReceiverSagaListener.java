package com.example.sagatransferdemo.saga;

import com.example.sagatransferdemo.accounts.receiver.ReceiverAccountStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Represents receiver-service's side of the saga: it only ever runs its own local transactions. */
@Component
public class ReceiverSagaListener {

    private final ReceiverAccountStore receiverAccountStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ReceiverSagaListener(ReceiverAccountStore receiverAccountStore, KafkaTemplate<String, Object> kafkaTemplate) {
        this.receiverAccountStore = receiverAccountStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.CREDIT_REQUESTED, groupId = "receiver-service")
    public void onCreditRequested(CreditCommand command) {
        if (command.simulateFailure()) {
            kafkaTemplate.send(SagaTopics.CREDIT_RESULT, command.sagaId(),
                    new StepResult(command.sagaId(), false, "Simulated receiver-service failure"));
            return;
        }
        receiverAccountStore.credit(command.accountId(), command.amount());
        kafkaTemplate.send(SagaTopics.CREDIT_RESULT, command.sagaId(), new StepResult(command.sagaId(), true, null));
    }
}
