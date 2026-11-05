package com.mccanto.mdp.quality;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

class DataFreshnessHealthIndicatorTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

	private final DataFreshnessTracker tracker = new DataFreshnessTracker(new DataFreshnessProperties(),
			new SimpleMeterRegistry(), clock);

	private final DataFreshnessHealthIndicator health = new DataFreshnessHealthIndicator(tracker);

	@Test
	void isUpWhenDataIsFresh() {
		assertThat(health.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void isDownWhenDataIsStale() {
		tracker.markIngested(Instant.parse("2026-11-05T09:00:00Z"));

		var result = health.health();

		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails().get("lagSeconds")).isEqualTo(10800L);
	}

}
