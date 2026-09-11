package com.example.sagatransferdemo.saga;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * THE FIX: instead of one call site assuming both steps happen atomically,
 * each step is its own local transaction (in sender-service or
 * receiver-service) and the orchestrator reacts to each step's outcome.
 * If the credit step fails, it explicitly runs the compensating action
 * (refund the sender) instead of leaving the money stuck. This trades
 * immediate atomicity for eventual consistency plus an explicit recovery
 * path for every way a step can fail.
 */
@Component
public class SagaOrchestrator {

    private final ConcurrentHashMap<String, SagaState> sagas = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SagaOrchestrator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public String start(String fromAccountId, String toAccountId, BigDecimal amount, boolean simulateCreditFailure) {
        String sagaId = java.util.UUID.randomUUID().toString();
        sagas.put(sagaId, new SagaState(sagaId, fromAccountId, toAccountId, amount, simulateCreditFailure,
                SagaStatus.STARTED, null));
        kafkaTemplate.send(SagaTopics.DEBIT_REQUESTED, sagaId, new TransferCommand(sagaId, fromAccountId, amount));
        return sagaId;
    }

    public SagaState getState(String sagaId) {
        return sagas.get(sagaId);
    }

    @KafkaListener(topics = SagaTopics.DEBIT_RESULT, groupId = "saga-orchestrator")
    public void onDebitResult(StepResult result) {
        sagas.computeIfPresent(result.sagaId(), (id, state) -> {
            if (result.success()) {
                kafkaTemplate.send(SagaTopics.CREDIT_REQUESTED, id,
                        new CreditCommand(id, state.toAccountId(), state.amount(), state.simulateCreditFailure()));
                return state.withStatus(SagaStatus.DEBITED, null);
            }
            return state.withStatus(SagaStatus.FAILED, result.reason());
        });
    }

    @KafkaListener(topics = SagaTopics.CREDIT_RESULT, groupId = "saga-orchestrator")
    public void onCreditResult(StepResult result) {
        sagas.computeIfPresent(result.sagaId(), (id, state) -> {
            if (result.success()) {
                return state.withStatus(SagaStatus.COMPLETED, null);
            }
            // Compensating action: the credit step failed after the debit
            // already committed, so undo the debit instead of leaving the
            // sender's money gone with nothing to show for it.
            kafkaTemplate.send(SagaTopics.REFUND_REQUESTED, id,
                    new TransferCommand(id, state.fromAccountId(), state.amount()));
            return state.withStatus(SagaStatus.COMPENSATING, result.reason());
        });
    }

    @KafkaListener(topics = SagaTopics.REFUND_RESULT, groupId = "saga-orchestrator")
    public void onRefundResult(StepResult result) {
        sagas.computeIfPresent(result.sagaId(),
                (id, state) -> state.withStatus(SagaStatus.COMPENSATED, state.reason()));
    }
}
