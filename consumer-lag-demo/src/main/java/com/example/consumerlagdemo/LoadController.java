package com.example.consumerlagdemo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Load", description = "Produce a sustained stream of events at a fixed rate")
public class LoadController {

    private final OrderProducer orderProducer;

    public LoadController(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    @Operation(summary = "Start producing load on a topic (async, returns immediately)")
    @PostMapping("/api/load/{topic}")
    public Map<String, Object> startLoad(
            @PathVariable String topic,
            @RequestParam(defaultValue = "150") int count,
            @RequestParam(defaultValue = "15") long intervalMillis) {
        orderProducer.produceLoadAsync(topic, count, intervalMillis);
        return Map.of("topic", topic, "count", count, "intervalMillis", intervalMillis, "status", "STARTED");
    }
}
