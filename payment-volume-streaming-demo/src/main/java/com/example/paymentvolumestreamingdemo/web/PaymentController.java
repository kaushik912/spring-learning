package com.example.paymentvolumestreamingdemo.web;

import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.model.PaymentEvent;
import com.example.paymentvolumestreamingdemo.web.dto.PaymentAcceptedResponse;
import com.example.paymentvolumestreamingdemo.web.dto.SubmitPaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments", description = "Submit payments that feed the real-time 24h volume aggregation")
public class PaymentController {

	private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

	public PaymentController(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	@Operation(
			summary = "Submit a payment",
			description = "Publishes a payment event. timestamp is optional and defaults to now; "
					+ "supplying a past timestamp is a way to demo FX-rate-at-event-time conversion.")
	@PostMapping("/api/payments")
	public ResponseEntity<PaymentAcceptedResponse> submitPayment(@Valid @RequestBody SubmitPaymentRequest request) {
		String id = UUID.randomUUID().toString();
		Instant timestamp = request.timestamp() != null ? request.timestamp() : Instant.now();
		PaymentEvent event = new PaymentEvent(id, request.amount(), request.currency(), timestamp);
		kafkaTemplate.send(PaymentTopics.TOPIC, event);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new PaymentAcceptedResponse(id, timestamp, request.currency(), request.amount()));
	}
}
