package com.example.resourceexhaustiondemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.resourceexhaustiondemo.memory.buggy.MemoryHogService;
import com.example.resourceexhaustiondemo.memory.fixed.StreamingReportService;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no Spring context - a booted ApplicationContext alone can
 * need well over 64MB, which would make the heap limit meaningless). Run
 * with the small heap that proves the claim:
 *
 * <pre>./mvnw test -Poom-demo</pre>
 *
 * Under the default heap this test isn't run at all (see the surefire
 * excludes in pom.xml) because it wouldn't prove anything - the buggy
 * service just wouldn't run out of memory.
 */
class OomReproductionTest {

    private static final int ROW_COUNT = 2_000; // 2,000 x 100KB rows = ~200MB total

    @Test
    void givenBuggyService_whenRowCountIsLarge_thenThrowsOutOfMemoryError() {
        MemoryHogService service = new MemoryHogService();

        // Buggy: keeps every row alive in one List for the whole call. At
        // ~100KB/row, ~640 rows already exceeds a 64MB heap - it never gets
        // anywhere close to the requested 2,000.
        assertThatThrownBy(() -> service.buildReport(ROW_COUNT))
                .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void givenFixedService_whenRowCountIsLarge_thenCompletesWithinTheSameConstrainedHeap() throws IOException {
        StreamingReportService service = new StreamingReportService();
        OutputStream sink = OutputStream.nullOutputStream();

        // Fixed: same total volume of data (~200MB written over the life of
        // the call), but each row is written and discarded immediately, so
        // at most one row (~100KB) is ever resident. Comfortably survives
        // the same 64MB heap that just killed the buggy path.
        long totalBytes = service.streamReport(ROW_COUNT, sink);

        assertThat(totalBytes).isEqualTo(ROW_COUNT * 100_000L);
    }
}
