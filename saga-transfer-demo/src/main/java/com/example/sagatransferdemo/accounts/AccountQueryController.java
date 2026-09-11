package com.example.sagatransferdemo.accounts;

import com.example.sagatransferdemo.accounts.receiver.ReceiverAccountStore;
import com.example.sagatransferdemo.accounts.sender.SenderAccountStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Accounts", description = "Read-only balance lookups on each service's own store")
public class AccountQueryController {

    private final SenderAccountStore senderAccountStore;
    private final ReceiverAccountStore receiverAccountStore;

    public AccountQueryController(SenderAccountStore senderAccountStore, ReceiverAccountStore receiverAccountStore) {
        this.senderAccountStore = senderAccountStore;
        this.receiverAccountStore = receiverAccountStore;
    }

    @Operation(summary = "Sender-service account balance")
    @GetMapping("/api/accounts/sender/{accountId}")
    public Map<String, BigDecimal> senderBalance(@PathVariable String accountId) {
        return Map.of("balance", senderAccountStore.getBalance(accountId));
    }

    @Operation(summary = "Receiver-service account balance")
    @GetMapping("/api/accounts/receiver/{accountId}")
    public Map<String, BigDecimal> receiverBalance(@PathVariable String accountId) {
        return Map.of("balance", receiverAccountStore.getBalance(accountId));
    }
}
