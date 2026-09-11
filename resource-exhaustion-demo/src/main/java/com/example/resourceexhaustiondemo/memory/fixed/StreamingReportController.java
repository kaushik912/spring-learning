package com.example.resourceexhaustiondemo.memory.fixed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Tag(name = "Fixed report", description = "Streams the report row by row instead of buffering it in memory")
public class StreamingReportController {

    private final StreamingReportService service;

    public StreamingReportController(StreamingReportService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/fixed/report", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Stream a report row by row, holding at most one row in memory at a time")
    public StreamingResponseBody report(@RequestParam int rows) {
        return out -> service.streamReport(rows, out);
    }
}
