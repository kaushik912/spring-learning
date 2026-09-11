package com.example.sagatransferdemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sagatransferdemo.accounts.receiver.ReceiverAccountStore;
import com.example.sagatransferdemo.accounts.sender.SenderAccountStore;
import com.example.sagatransferdemo.buggy.TransferRequest;
import com.example.sagatransferdemo.saga.SagaState;
import com.example.sagatransferdemo.saga.SagaStatus;
import com.example.sagatransferdemo.saga.SagaTransferRequest;
import com.example.sagatransferdemo.saga.SagaTopics;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Reproduces the exact scenario from the prompt: sender and receiver
 * accounts live in two separate stores (standing in for two services' own
 * databases), and a crash between the debit committing and the credit
 * running leaves money stuck when there's no saga to recover it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@EmbeddedKafka(
        partitions = 1,
        topics = {
            SagaTopics.DEBIT_REQUESTED, SagaTopics.DEBIT_RESULT,
            SagaTopics.CREDIT_REQUESTED, SagaTopics.CREDIT_RESULT,
            SagaTopics.REFUND_REQUESTED, SagaTopics.REFUND_RESULT
        })
class TransferSagaReproductionTest {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("30.00");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SenderAccountStore senderAccountStore;

    @Autowired
    private ReceiverAccountStore receiverAccountStore;

    @Test
    void givenNoSharedTransaction_whenCrashHappensAfterDebitBeforeCredit_thenMoneyIsStuck() {
        String fromAccountId = seedSenderAccount();
        String toAccountId = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, TRANSFER_AMOUNT, true);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/buggy/transfers"), request, String.class);

        // The request blows up (simulated crash) - but the debit already committed.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(senderAccountStore.getBalance(fromAccountId))
                .isEqualByComparingTo(INITIAL_BALANCE.subtract(TRANSFER_AMOUNT));
        assertThat(receiverAccountStore.getBalance(toAccountId)).isEqualByComparingTo(BigDecimal.ZERO);
        // The money is gone from the sender and never arrived at the receiver.
    }

    @Test
    void givenSaga_whenTransferSucceeds_thenBothSidesEndUpConsistent() throws Exception {
        String fromAccountId = seedSenderAccount();
        String toAccountId = UUID.randomUUID().toString();

        SagaTransferRequest request = new SagaTransferRequest(fromAccountId, toAccountId, TRANSFER_AMOUNT, false);
        ResponseEntity<Map> started = restTemplate.postForEntity(url("/api/saga/transfers"), request, Map.class);
        String sagaId = (String) started.getBody().get("sagaId");

        SagaState finalState = awaitTerminalState(sagaId);

        assertThat(finalState.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(senderAccountStore.getBalance(fromAccountId))
                .isEqualByComparingTo(INITIAL_BALANCE.subtract(TRANSFER_AMOUNT));
        assertThat(receiverAccountStore.getBalance(toAccountId)).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Test
    void givenSaga_whenCreditStepFails_thenDebitIsCompensatedAndSenderEndsUpWhole() throws Exception {
        String fromAccountId = seedSenderAccount();
        String toAccountId = UUID.randomUUID().toString();

        SagaTransferRequest request = new SagaTransferRequest(fromAccountId, toAccountId, TRANSFER_AMOUNT, true);
        ResponseEntity<Map> started = restTemplate.postForEntity(url("/api/saga/transfers"), request, Map.class);
        String sagaId = (String) started.getBody().get("sagaId");

        SagaState finalState = awaitTerminalState(sagaId);

        assertThat(finalState.status()).isEqualTo(SagaStatus.COMPENSATED);
        // Unlike the buggy flow: the sender ends up with their money back.
        assertThat(senderAccountStore.getBalance(fromAccountId)).isEqualByComparingTo(INITIAL_BALANCE);
        assertThat(receiverAccountStore.getBalance(toAccountId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String seedSenderAccount() {
        String accountId = UUID.randomUUID().toString();
        senderAccountStore.refund(accountId, INITIAL_BALANCE);
        return accountId;
    }

    private SagaState awaitTerminalState(String sagaId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<SagaState> response =
                    restTemplate.getForEntity(url("/api/saga/transfers/" + sagaId), SagaState.class);
            SagaState state = response.getBody();
            if (state != null && (state.status() == SagaStatus.COMPLETED
                    || state.status() == SagaStatus.COMPENSATED
                    || state.status() == SagaStatus.FAILED)) {
                return state;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Saga " + sagaId + " did not reach a terminal state within the timeout");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
