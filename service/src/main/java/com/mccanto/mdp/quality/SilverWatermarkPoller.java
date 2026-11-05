package com.mccanto.mdp.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the watermark written by the Spark job so every replica observes the
 * same ingestion time instead of its own in-memory state.
 */
@Component
public class SilverWatermarkPoller {

	private static final Logger log = LoggerFactory.getLogger(SilverWatermarkPoller.class);

	private final DataFreshnessTracker tracker;

	private final DataFreshnessProperties properties;

	private final ObjectMapper mapper;

	public SilverWatermarkPoller(DataFreshnessTracker tracker, DataFreshnessProperties properties,
			ObjectMapper mapper) {
		this.tracker = tracker;
		this.properties = properties;
		this.mapper = mapper;
	}

	@Scheduled(fixedDelayString = "${mdp.data.freshness.watermark-poll:30s}")
	public void poll() {
		String configured = properties.getWatermarkPath();
		if (configured == null || configured.isBlank()) {
			return;
		}
		Path path = Path.of(configured);
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			JsonNode lastIngestion = mapper.readTree(Files.readString(path)).get("lastIngestion");
			if (lastIngestion == null || lastIngestion.isNull()) {
				return;
			}
			Instant at = Instant.parse(lastIngestion.asText());
			if (at.isAfter(tracker.lastIngestion())) {
				tracker.markIngested(at);
				log.info("Silver watermark advanced to {}", at);
			}
		}
		catch (IOException | DateTimeParseException ex) {
			log.warn("Ignoring unreadable silver watermark at {}: {}", path, ex.getMessage());
		}
	}

}
