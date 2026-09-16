package com.example.ioexhaustiondemo.shared;

import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/**
 * Stands in for a slow downstream dependency (inventory service, payment
 * gateway, etc). Runs on its own OS-assigned port and its own thread pool so
 * it can never be the resource under test - only the calling app's own
 * thread-pool behavior (buggy vs fixed) matters for this demo.
 */
@Component
public class DownstreamAvailabilityServer {

    private HttpServer server;

    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/availability", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        long delayMs = parseDelayMs(exchange.getRequestURI().getQuery());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = ("{\"available\":true,\"delayMs\":" + delayMs + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static long parseDelayMs(String query) {
        if (query == null) {
            return 0L;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals("delayMs")) {
                return Long.parseLong(kv[1]);
            }
        }
        return 0L;
    }
}
