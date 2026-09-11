package com.example.sagatransferdemo.buggy;

import com.example.sagatransferdemo.accounts.receiver.ReceiverAccountStore;
import com.example.sagatransferdemo.accounts.sender.SenderAccountStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE BUG: treats "debit the sender" and "credit the receiver" as if they
 * were one atomic step, even though they're local transactions in two
 * separate services with two separate databases. When both accounts live in
 * the same database, a crash here would roll back the whole transaction.
 * They don't — there is no transaction that spans both stores, so a crash
 * after the debit commits leaves the money nowhere: gone from the sender,
 * never arrived at the receiver.
 */
@RestController
@Tag(name = "Buggy", description = "Debit-then-credit across two services with no shared transaction")
public class BuggyTransferController {

    private final SenderAccountStore senderAccountStore;
    private final ReceiverAccountStore receiverAccountStore;

    public BuggyTransferController(SenderAccountStore senderAccountStore, ReceiverAccountStore receiverAccountStore) {
        this.senderAccountStore = senderAccountStore;
        this.receiverAccountStore = receiverAccountStore;
    }

    @Operation(summary = "Transfer money (buggy: no distributed transaction, no compensation)")
    @PostMapping("/api/buggy/transfers")
    public Map<String, Object> transfer(@RequestBody TransferRequest request) {
        // Step 1: sender-service's own local transaction. Commits immediately
        // and permanently — there is nothing tying it to step 2 below.
        boolean debited = senderAccountStore.debit(request.fromAccountId(), request.amount());
        if (!debited) {
            return Map.of("status", "FAILED", "reason", "insufficient funds");
        }

        if (request.simulateCrash()) {
            // Simulates the process/receiver-service/network dying right
            // here — after the sender's debit committed, before the
            // receiver's credit ever runs. In a single-database world this
            // would just roll back. Across two databases, it can't.
            throw new IllegalStateException(
                    "Simulated crash: process died after the debit committed, before the credit could run");
        }

        // Step 2: receiver-service's own, entirely separate local transaction.
        receiverAccountStore.credit(request.toAccountId(), request.amount());
        return Map.of("status", "COMPLETED");
    }
}
