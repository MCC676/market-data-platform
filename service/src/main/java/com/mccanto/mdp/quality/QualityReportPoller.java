package com.mccanto.mdp.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exposes the last pipeline quality report as Prometheus gauges. Quality
 * failures are alerting signals, not readiness signals: bad rows do not make
 * the API unable to serve.
 */
@Component
public class QualityReportPoller {

	private static final Logger log = LoggerFactory.getLogger(QualityReportPoller.class);

	private final QualityProperties properties;

	private final ObjectMapper mapper;

	private final AtomicLong extractedRows = new AtomicLong();

	private final AtomicLong rejectedRows = new AtomicLong();

	private final AtomicLong duplicateRows = new AtomicLong();

	private final AtomicLong invalidStatusRows = new AtomicLong();

	private final AtomicLong bronzeRows = new AtomicLong();

	private final AtomicLong silverRows = new AtomicLong();

	private final AtomicLong goldRows = new AtomicLong();

	private final AtomicLong reportTimestampSeconds = new AtomicLong();

	public QualityReportPoller(QualityProperties properties, ObjectMapper mapper, MeterRegistry registry) {
		this.properties = properties;
		this.mapper = mapper;
		gauge(registry, "mdp.quality.extracted.rows", "Rows read from the last extract", extractedRows);
		gauge(registry, "mdp.quality.rejected.rows", "Rows rejected for a missing id", rejectedRows);
		gauge(registry, "mdp.quality.duplicate.rows", "Duplicate ids collapsed by the last run", duplicateRows);
		gauge(registry, "mdp.quality.invalid.status.rows", "Rows with an unknown status", invalidStatusRows);
		gauge(registry, "mdp.quality.bronze.rows", "Rows in the bronze table", bronzeRows);
		gauge(registry, "mdp.quality.silver.rows", "Rows in the silver table", silverRows);
		gauge(registry, "mdp.quality.gold.rows", "Rows in the gold table", goldRows);
		gauge(registry, "mdp.quality.report.timestamp.seconds", "Epoch seconds of the last quality report",
				reportTimestampSeconds);
	}

	@Scheduled(fixedDelayString = "${mdp.data.quality.report-poll:30s}")
	public void poll() {
		String configured = properties.getReportPath();
		if (configured == null || configured.isBlank()) {
			return;
		}
		Path path = Path.of(configured);
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			JsonNode report = mapper.readTree(Files.readString(path));
			extractedRows.set(longValue(report, "extractedRows"));
			rejectedRows.set(longValue(report, "rejectedRows"));
			duplicateRows.set(longValue(report, "duplicateRows"));
			invalidStatusRows.set(longValue(report, "invalidStatusRows"));
			bronzeRows.set(longValue(report, "bronzeRows"));
			silverRows.set(longValue(report, "silverRows"));
			goldRows.set(longValue(report, "goldRows"));
			JsonNode finishedAt = report.get("finishedAt");
			if (finishedAt != null && !finishedAt.isNull()) {
				reportTimestampSeconds.set(Instant.parse(finishedAt.asText()).getEpochSecond());
			}
		}
		catch (IOException | DateTimeParseException ex) {
			log.warn("Ignoring unreadable quality report at {}: {}", path, ex.getMessage());
		}
	}

	private static long longValue(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? 0 : value.asLong(0);
	}

	private static void gauge(MeterRegistry registry, String name, String description, AtomicLong value) {
		Gauge.builder(name, value, AtomicLong::doubleValue).description(description).register(registry);
	}

}
