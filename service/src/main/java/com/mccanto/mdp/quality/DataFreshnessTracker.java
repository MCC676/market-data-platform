package com.mccanto.mdp.quality;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DataFreshnessTracker {

	private final AtomicReference<Instant> lastIngestion;

	private final DataFreshnessProperties properties;

	private final Clock clock;

	public DataFreshnessTracker(DataFreshnessProperties properties, MeterRegistry registry, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.lastIngestion = new AtomicReference<>(clock.instant());
		Gauge.builder("mdp.data.last.ingestion.timestamp.seconds", lastIngestion,
						ref -> (double) ref.get().getEpochSecond())
				.description("Epoch seconds of the last successful ingestion")
				.register(registry);
		Gauge.builder("mdp.data.lag.seconds", this, DataFreshnessTracker::lagSeconds)
				.description("Seconds elapsed since the last successful ingestion")
				.register(registry);
	}

	public void markIngested() {
		markIngested(clock.instant());
	}

	public void markIngested(Instant at) {
		lastIngestion.set(at);
	}

	public Instant lastIngestion() {
		return lastIngestion.get();
	}

	public long lagSeconds() {
		return Math.max(0, Duration.between(lastIngestion.get(), clock.instant()).toSeconds());
	}

	public boolean isStale() {
		return lagSeconds() > threshold().toSeconds();
	}

	public Duration threshold() {
		return properties.getThreshold();
	}

}
