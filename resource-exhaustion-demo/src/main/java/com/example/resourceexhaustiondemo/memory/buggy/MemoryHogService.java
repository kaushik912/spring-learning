package com.example.resourceexhaustiondemo.memory.buggy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MemoryHogService {

    private static final int ROW_SIZE_BYTES = 100_000;

    /**
     * Stand-in for {@code SELECT * FROM huge_table} mapped straight into a
     * {@code List<Entity>} and returned. Every row's payload is allocated and
     * kept alive in one list for the entire lifetime of the request - the
     * more rows the caller asks for, the more memory is resident at once,
     * with no upper bound.
     */
    public List<byte[]> buildReport(int rowCount) {
        List<byte[]> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.add(new byte[ROW_SIZE_BYTES]);
        }
        return rows;
    }
}
