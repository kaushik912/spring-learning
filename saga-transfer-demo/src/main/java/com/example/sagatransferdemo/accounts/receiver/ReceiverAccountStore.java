package com.example.sagatransferdemo.accounts.receiver;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stands in for the receiver-service's own database — a separate store from
 * {@link com.example.sagatransferdemo.accounts.sender.SenderAccountStore},
 * exactly like two microservices would each own their own database.
 */
@Component
public class ReceiverAccountStore {

    private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();

    public ReceiverAccountStore() {
        balances.put("bob", new BigDecimal("50.00"));
    }

    public BigDecimal getBalance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    /** The receiver-service's local transaction: credit the account. */
    public void credit(String accountId, BigDecimal amount) {
        balances.merge(accountId, amount, BigDecimal::add);
    }
}
