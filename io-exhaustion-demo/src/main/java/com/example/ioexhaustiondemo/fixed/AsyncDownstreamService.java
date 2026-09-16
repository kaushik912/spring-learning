package com.example.ioexhaustiondemo.fixed;

import com.example.ioexhaustiondemo.shared.DownstreamAvailabilityServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Same blocking downstream call as the buggy service, but run on the
 * dedicated {@code downstreamExecutor} instead of the Tomcat request
 * thread - the Tomcat thread returns immediately after submitting the work.
 */
@Component
public class AsyncDownstreamService {

    private final DownstreamAvailabilityServer downstream;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AsyncDownstreamService(DownstreamAvailabilityServer downstream) {
        this.downstream = downstream;
    }

    @Async("downstreamExecutor")
    public CompletableFuture<Map<String, Object>> checkAvailabilityAsync(long delayMs) {
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
        return CompletableFuture.completedFuture(Map.of(
                "available", true,
                "delayMs", delayMs,
                "elapsedMs", elapsedMs,
                "servedByThread", Thread.currentThread().getName()));
    }
}
