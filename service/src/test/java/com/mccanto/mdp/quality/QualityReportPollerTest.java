package com.mccanto.mdp.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QualityReportPollerTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final QualityProperties properties = new QualityProperties();

	private final QualityReportPoller poller = new QualityReportPoller(properties, new ObjectMapper(), registry);

	@TempDir
	Path tempDir;

	@Test
	void publishesTheLastQualityReportAsGauges() throws IOException {
		useReport("""
				{
				  "extractedRows": 2,
				  "rejectedRows": 1,
				  "duplicateRows": 0,
				  "invalidStatusRows": 1,
				  "bronzeRows": 10,
				  "silverRows": 6,
				  "goldRows": 5,
				  "finishedAt": "2026-11-05T20:40:41Z"
				}
				""");

		poller.poll();

		assertThat(gauge("mdp.quality.extracted.rows")).isEqualTo(2.0);
		assertThat(gauge("mdp.quality.rejected.rows")).isEqualTo(1.0);
		assertThat(gauge("mdp.quality.invalid.status.rows")).isEqualTo(1.0);
		assertThat(gauge("mdp.quality.silver.rows")).isEqualTo(6.0);
		assertThat(gauge("mdp.quality.report.timestamp.seconds"))
				.isEqualTo((double) java.time.Instant.parse("2026-11-05T20:40:41Z").getEpochSecond());
	}

	@Test
	void ignoresMissingReport() {
		properties.setReportPath(tempDir.resolve("missing.json").toString());

		assertThatCode(poller::poll).doesNotThrowAnyException();
		assertThat(gauge("mdp.quality.rejected.rows")).isZero();
	}

	@Test
	void ignoresMalformedReport() throws IOException {
		properties.setReportPath(tempDir.resolve("broken.json").toString());
		Files.writeString(tempDir.resolve("broken.json"), "{\"finishedAt\": \"not-a-timestamp\"}");

		assertThatCode(poller::poll).doesNotThrowAnyException();
		assertThat(gauge("mdp.quality.rejected.rows")).isZero();
	}

	private void useReport(String json) throws IOException {
		Path file = tempDir.resolve("latest.json");
		Files.writeString(file, json);
		properties.setReportPath(file.toString());
	}

	private double gauge(String name) {
		return registry.get(name).gauge().value();
	}

}
