package com.example.paymentvolumestreamingdemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.model.Currency;
import com.example.paymentvolumestreamingdemo.web.dto.PaymentAcceptedResponse;
import com.example.paymentvolumestreamingdemo.web.dto.PaymentVolumeResponse;
import com.example.paymentvolumestreamingdemo.web.dto.SubmitPaymentRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The one true end-to-end test: real (embedded) broker, real Kafka Streams topology,
 * real REST layer. Window/commit-interval are shrunk via @DynamicPropertySource so a
 * window is observably populated within the test timeout, and polling is bounded-retry
 * rather than a fixed sleep.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@EmbeddedKafka(topics = PaymentTopics.TOPIC)
class PaymentApiIntegrationTest {

	@DynamicPropertySource
	static void overrideProperties(DynamicPropertyRegistry registry) throws IOException {
		Path stateDir = Files.createTempDirectory("payment-volume-it-");
		registry.add("spring.kafka.streams.properties.state.dir", stateDir::toString);
		registry.add("spring.kafka.streams.properties.commit.interval.ms", () -> "200");
		// advance == size (effectively tumbling) so payments submitted a few hundred ms
		// apart can't straddle two overlapping windows - that hopping-window edge case
		// is covered precisely, with controlled timestamps, by PaymentVolumeTopologyTest.
		registry.add("payments.window.size", () -> "PT10S");
		registry.add("payments.window.advance", () -> "PT10S");
		registry.add("payments.window.grace", () -> "PT1S");
		registry.add("payments.simulator.enabled", () -> "false");
	}

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void givenPaymentsAcrossAllThreeCurrencies_whenSubmittedViaApi_thenVolumeEndpointSumsAllInUsd() {
		// Given
		submitPayment(BigDecimal.valueOf(100), Currency.USD);
		submitPayment(BigDecimal.valueOf(100), Currency.EUR);
		submitPayment(BigDecimal.valueOf(100), Currency.GBP);

		// When
		PaymentVolumeResponse response = pollUntil(
				r -> r.currentWindow() != null
						&& !"NO_DATA".equals(r.currentWindow().status())
						&& r.currentWindow().totalUsd().compareTo(BigDecimal.ZERO) > 0,
				Duration.ofSeconds(25));

		// Then
		assertThat(response.currentWindow().totalUsd()).isGreaterThan(BigDecimal.valueOf(300));
	}

	private void submitPayment(BigDecimal amount, Currency currency) {
		SubmitPaymentRequest request = new SubmitPaymentRequest(amount, currency, Instant.now());
		restTemplate.postForEntity(url("/api/payments"), request, PaymentAcceptedResponse.class);
	}

	private PaymentVolumeResponse getVolume() {
		return restTemplate.getForObject(url("/api/payments/volume"), PaymentVolumeResponse.class);
	}

	private PaymentVolumeResponse pollUntil(Predicate<PaymentVolumeResponse> condition, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		PaymentVolumeResponse last = null;
		while (Instant.now().isBefore(deadline)) {
			last = getVolume();
			if (condition.test(last)) {
				return last;
			}
			sleep();
		}
		throw new AssertionError("Condition not met within " + timeout + "; last response: " + last);
	}

	private void sleep() {
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
