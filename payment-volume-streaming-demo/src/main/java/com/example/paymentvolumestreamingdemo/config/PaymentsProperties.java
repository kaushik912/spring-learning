package com.example.paymentvolumestreamingdemo.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public class PaymentsProperties {

	private final Window window = new Window();
	private final Fx fx = new Fx();
	private final Simulator simulator = new Simulator();

	public Window getWindow() {
		return window;
	}

	public Fx getFx() {
		return fx;
	}

	public Simulator getSimulator() {
		return simulator;
	}

	public static class Window {
		private Duration size = Duration.ofHours(24);
		private Duration advance = Duration.ofHours(1);
		private Duration grace = Duration.ofMinutes(5);

		public Duration getSize() {
			return size;
		}

		public void setSize(Duration size) {
			this.size = size;
		}

		public Duration getAdvance() {
			return advance;
		}

		public void setAdvance(Duration advance) {
			this.advance = advance;
		}

		public Duration getGrace() {
			return grace;
		}

		public void setGrace(Duration grace) {
			this.grace = grace;
		}
	}

	public static class Fx {
		private Duration jitterInterval = Duration.ofSeconds(30);
		private double jitterMagnitudePercent = 1.5;

		public Duration getJitterInterval() {
			return jitterInterval;
		}

		public void setJitterInterval(Duration jitterInterval) {
			this.jitterInterval = jitterInterval;
		}

		public double getJitterMagnitudePercent() {
			return jitterMagnitudePercent;
		}

		public void setJitterMagnitudePercent(double jitterMagnitudePercent) {
			this.jitterMagnitudePercent = jitterMagnitudePercent;
		}
	}

	public static class Simulator {
		private boolean enabled = true;
		private Duration interval = Duration.ofMillis(500);
		private BigDecimal minAmount = BigDecimal.valueOf(10);
		private BigDecimal maxAmount = BigDecimal.valueOf(5000);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getInterval() {
			return interval;
		}

		public void setInterval(Duration interval) {
			this.interval = interval;
		}

		public BigDecimal getMinAmount() {
			return minAmount;
		}

		public void setMinAmount(BigDecimal minAmount) {
			this.minAmount = minAmount;
		}

		public BigDecimal getMaxAmount() {
			return maxAmount;
		}

		public void setMaxAmount(BigDecimal maxAmount) {
			this.maxAmount = maxAmount;
		}
	}
}
