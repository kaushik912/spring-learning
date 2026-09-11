package com.example.consumerlagdemo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Lag", description = "Real consumer lag: log end offset minus committed offset, per group")
public class LagController {

    private final ConsumerLagService consumerLagService;

    public LagController(ConsumerLagService consumerLagService) {
        this.consumerLagService = consumerLagService;
    }

    @Operation(summary = "Total lag for a consumer group on a topic")
    @GetMapping("/api/lag/{groupId}/{topic}")
    public Map<String, Object> getLag(@PathVariable String groupId, @PathVariable String topic) {
        return Map.of(
                "groupId", groupId,
                "topic", topic,
                "totalLag", consumerLagService.getTotalLag(groupId, topic),
                "perPartitionLag", consumerLagService.getPerPartitionLag(groupId, topic));
    }
}
