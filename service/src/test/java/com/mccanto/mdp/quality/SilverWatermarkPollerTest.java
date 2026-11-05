package com.mccanto.mdp.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SilverWatermarkPollerTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

	private final DataFreshnessProperties properties = new DataFreshnessProperties();

	private final DataFreshnessTracker tracker = new DataFreshnessTracker(properties,
			new SimpleMeterRegistry(), clock);

	private final SilverWatermarkPoller poller = new SilverWatermarkPoller(tracker, properties,
			new ObjectMapper());

	@TempDir
	Path tempDir;

	@Test
	void advancesFreshnessWhenWatermarkIsNewer() throws IOException {
		useWatermark("2026-11-05T12:05:00Z");

		poller.poll();

		assertThat(tracker.lastIngestion()).isEqualTo(Instant.parse("2026-11-05T12:05:00Z"));
	}

	@Test
	void ignoresOlderWatermark() throws IOException {
		useWatermark("2026-11-05T11:00:00Z");

		poller.poll();

		assertThat(tracker.lastIngestion()).isEqualTo(clock.instant());
	}

	@Test
	void ignoresMissingWatermark() {
		properties.setWatermarkPath(tempDir.resolve("missing.json").toString());

		assertThatCode(poller::poll).doesNotThrowAnyException();
		assertThat(tracker.lastIngestion()).isEqualTo(clock.instant());
	}

	@Test
	void ignoresMalformedWatermark() throws IOException {
		properties.setWatermarkPath(tempDir.resolve("broken.json").toString());
		Files.writeString(tempDir.resolve("broken.json"), "{\"lastIngestion\": \"not-a-timestamp\"}");

		assertThatCode(poller::poll).doesNotThrowAnyException();
		assertThat(tracker.lastIngestion()).isEqualTo(clock.instant());
	}

	private void useWatermark(String lastIngestion) throws IOException {
		Path file = tempDir.resolve("shipments.json");
		Files.writeString(file, "{\"lastIngestion\": \"" + lastIngestion + "\"}");
		properties.setWatermarkPath(file.toString());
	}

}
