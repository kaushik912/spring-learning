package com.example.resourceexhaustiondemo.memory.fixed;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;

@Component
public class StreamingReportService {

    private static final int ROW_SIZE_BYTES = 100_000;

    /**
     * Same total volume of data as {@code MemoryHogService}, but each row is
     * written to the output stream and flushed immediately instead of being
     * appended to a list. No reference to a row survives past the write, so
     * at most one row's worth of memory (~100KB) is ever resident at once -
     * total peak memory no longer scales with rowCount.
     */
    public long streamReport(int rowCount, OutputStream out) throws IOException {
        long totalBytes = 0;
        for (int i = 0; i < rowCount; i++) {
            byte[] row = new byte[ROW_SIZE_BYTES];
            out.write(row);
            totalBytes += row.length;
        }
        out.flush();
        return totalBytes;
    }
}
