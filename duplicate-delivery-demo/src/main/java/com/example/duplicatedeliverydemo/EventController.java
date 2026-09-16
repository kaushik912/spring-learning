package com.example.duplicatedeliverydemo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Duplicate Delivery Demo", description = "Publish events and inspect side-effect counts")
public class EventController {

    private final EventProducer eventProducer;
    private final EmailService emailService;

    public EventController(EventProducer eventProducer, EmailService emailService) {
        this.eventProducer = eventProducer;
        this.emailService = emailService;
    }

    @PostMapping("/publish/{topic}")
    @Operation(summary = "Publish one event to the given topic and return its eventId")
    public Map<String, String> publish(@PathVariable String topic) {
        String eventId = eventProducer.publish(topic);
        return Map.of("eventId", eventId, "topic", topic);
    }

    @GetMapping("/email-count/{eventId}")
    @Operation(summary = "How many times sendConfirmation ran for this eventId")
    public Map<String, Object> emailCount(@PathVariable String eventId) {
        return Map.of("eventId", eventId, "count", emailService.countFor(eventId));
    }
}
