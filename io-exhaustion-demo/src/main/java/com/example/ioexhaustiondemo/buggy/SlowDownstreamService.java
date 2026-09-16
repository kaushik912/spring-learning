package com.example.ioexhaustiondemo.buggy;

import com.example.ioexhaustiondemo.shared.DownstreamAvailabilityServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Checks inventory availability by calling a downstream service
 * synchronously - blocks the calling thread on a real socket read for the
 * full duration of the downstream call.
 */
@Component
public class SlowDownstreamService {

    private final DownstreamAvailabilityServer downstream;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SlowDownstreamService(DownstreamAvailabilityServer downstream) {
        this.downstream = downstream;
    }

    public Map<String, Object> checkAvailability(long delayMs) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + downstream.getPort() + "/availability?delayMs=" + delayMs))
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for downstream call", e);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return Map.of(
                "available", true,
                "delayMs", delayMs,
                "elapsedMs", elapsedMs,
                "servedByThread", Thread.currentThread().getName());
    }
}
