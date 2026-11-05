package com.mccanto.mdp.quality;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes data freshness as a health signal. It is included in the readiness
 * group only: stale data should stop new traffic, but it must not restart the pod.
 */
@Component
public class DataFreshnessHealthIndicator implements HealthIndicator {

	private final DataFreshnessTracker tracker;

	public DataFreshnessHealthIndicator(DataFreshnessTracker tracker) {
		this.tracker = tracker;
	}

	@Override
	public Health health() {
		Health.Builder builder = tracker.isStale() ? Health.down() : Health.up();
		return builder
				.withDetail("lastIngestion", tracker.lastIngestion().toString())
				.withDetail("lagSeconds", tracker.lagSeconds())
				.withDetail("thresholdSeconds", tracker.threshold().toSeconds())
				.build();
	}

}
