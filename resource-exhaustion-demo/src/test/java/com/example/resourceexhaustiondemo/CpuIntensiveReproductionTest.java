package com.example.resourceexhaustiondemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.resourceexhaustiondemo.cpu.buggy.SlowDuplicateFinder;
import com.example.resourceexhaustiondemo.cpu.fixed.FastDuplicateFinder;
import com.example.resourceexhaustiondemo.cpu.shared.OrderIdGenerator;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * No special heap or profile needed here - unlike the OOM case, this bug
 * doesn't crash anything, it just burns CPU for far longer than it should.
 * Runs as part of the normal `mvn test`.
 */
class CpuIntensiveReproductionTest {

    @Test
    void givenSameInput_whenComparingBuggyVsFixed_thenBothAgreeButBuggyIsDramaticallySlower() {
        List<String> orderIds = OrderIdGenerator.generate(8_000, 500);

        TimedResult<List<String>> buggy = time(() -> new SlowDuplicateFinder().findDuplicates(orderIds));
        TimedResult<List<String>> fixed = time(() -> new FastDuplicateFinder().findDuplicates(orderIds));

        // Same correct answer either way - the bug isn't wrong output,
        // it's an O(n^2) scan where an O(n) one would do.
        assertThat(buggy.value()).containsExactlyInAnyOrderElementsOf(fixed.value());

        // At 8,000 rows the nested-loop version is already an order of
        // magnitude slower than the HashSet version.
        assertThat(buggy.elapsedMs()).isGreaterThan(fixed.elapsedMs() * 5);
    }

    private <T> TimedResult<T> time(Supplier<T> work) {
        long start = System.nanoTime();
        T result = work.get();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new TimedResult<>(result, Math.max(elapsedMs, 1));
    }

    private record TimedResult<T>(T value, long elapsedMs) {
    }
}
