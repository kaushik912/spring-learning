package com.example.paymentvolumestreamingdemo.web;

import com.example.paymentvolumestreamingdemo.config.PaymentsProperties;
import com.example.paymentvolumestreamingdemo.kafka.PaymentTopics;
import com.example.paymentvolumestreamingdemo.web.dto.PaymentVolumeResponse;
import com.example.paymentvolumestreamingdemo.web.dto.WindowTotal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.kafka.streams.KafkaStreamsInteractiveQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payment Volume", description = "Query the continuously-updated trailing payment volume in USD")
public class PaymentVolumeController {

	private final KafkaStreamsInteractiveQueryService interactiveQueryService;
	private final PaymentsProperties properties;

	public PaymentVolumeController(
			KafkaStreamsInteractiveQueryService interactiveQueryService, PaymentsProperties properties) {
		this.interactiveQueryService = interactiveQueryService;
		this.properties = properties;
	}

	@Operation(
			summary = "Get the trailing payment volume in USD",
			description = "Returns both the still-filling current window and the latest fully-closed window, "
					+ "since hopping windows never have one window that is exactly \"the last 24h\".")
	@GetMapping("/api/payments/volume")
	public PaymentVolumeResponse getVolume() {
		Instant now = Instant.now();
		Duration size = properties.getWindow().getSize();
		Duration advance = properties.getWindow().getAdvance();
		Instant from = now.minus(size).minus(advance);

		ReadOnlyWindowStore<String, BigDecimal> store =
				interactiveQueryService.retrieveQueryableStore(PaymentTopics.STORE_NAME, QueryableStoreTypes.windowStore());

		List<KeyValue<Long, BigDecimal>> windows = new ArrayList<>();
		try (WindowStoreIterator<BigDecimal> iterator = store.fetch(PaymentTopics.AGGREGATE_KEY, from, now)) {
			iterator.forEachRemaining(windows::add);
		}

		WindowTotal current = windows.stream()
				.filter(kv -> kv.key <= now.toEpochMilli())
				.max(Comparator.comparingLong(kv -> kv.key))
				.map(kv -> toWindowTotal(kv.key, kv.value, size, "IN_PROGRESS"))
				.orElseGet(WindowTotal::noData);

		long completedCutoff = now.minus(size).toEpochMilli();
		WindowTotal completed = windows.stream()
				.filter(kv -> kv.key <= completedCutoff)
				.max(Comparator.comparingLong(kv -> kv.key))
				.map(kv -> toWindowTotal(kv.key, kv.value, size, "COMPLETED"))
				.orElseGet(WindowTotal::noData);

		return new PaymentVolumeResponse(now, current, completed);
	}

	private WindowTotal toWindowTotal(long startMillis, BigDecimal total, Duration size, String status) {
		Instant start = Instant.ofEpochMilli(startMillis);
		return new WindowTotal(start, start.plus(size), total, status);
	}
}
