package com.example.dbioexhaustiondemo.buggy;

/**
 * Registered as an H2 ALIAS function so it runs once per row scanned.
 * H2's in-memory storage has no disk I/O to pay for, so a plain string
 * comparison over even a million rows is sub-20ms once JIT-warmed - too
 * fast to exhaust a connection pool. This stands in for the per-row cost
 * a real full scan often does pay for (decrypting a column, normalizing
 * data, page reads for cold cache) so the scan is reliably slow across
 * machines without needing an unrealistically large table.
 */
public final class SlowEmailMatcher {

    private static final int ROUNDS = 1_750;

    // volatile write below prevents the JIT from proving the loop is dead code.
    private static volatile long sink;

    private SlowEmailMatcher() {
    }

    public static boolean matches(String candidateEmail, String targetEmail) {
        simulatePerRowCost(candidateEmail);
        return candidateEmail != null && candidateEmail.equalsIgnoreCase(targetEmail);
    }

    private static void simulatePerRowCost(String value) {
        long acc = value == null ? 0 : value.hashCode();
        for (int round = 0; round < ROUNDS; round++) {
            acc = Long.rotateLeft(acc * 0x9E3779B97F4A7C15L + round, 13);
        }
        sink = acc;
    }
}
