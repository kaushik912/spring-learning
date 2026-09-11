package com.example.sagatransferdemo.accounts.sender;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stands in for the sender-service's own database. In a real system this
 * would be a separate service with its own schema/instance — the point is
 * that debit() here commits on its own, with no transaction that also spans
 * the receiver's store.
 */
@Component
public class SenderAccountStore {

    private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();

    public SenderAccountStore() {
        balances.put("alice", new BigDecimal("100.00"));
    }

    public BigDecimal getBalance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    /** The sender-service's local transaction: debit if funds allow. */
    public synchronized boolean debit(String accountId, BigDecimal amount) {
        BigDecimal current = getBalance(accountId);
        if (current.compareTo(amount) < 0) {
            return false;
        }
        balances.put(accountId, current.subtract(amount));
        return true;
    }

    /** The compensating action for a failed transfer: credit the money back. */
    public synchronized void refund(String accountId, BigDecimal amount) {
        balances.merge(accountId, amount, BigDecimal::add);
    }
}
