package com.mccanto.mdp.shipment;

import java.time.Instant;
import java.util.List;

import com.mccanto.mdp.quality.DataFreshnessTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShipmentService {

	private final ShipmentRepository repository;

	private final DataFreshnessTracker freshness;

	private final Counter ingestedRecords;

	public ShipmentService(ShipmentRepository repository, DataFreshnessTracker freshness, MeterRegistry registry) {
		this.repository = repository;
		this.freshness = freshness;
		this.ingestedRecords = Counter.builder("mdp.ingest.records")
				.description("Total shipment records loaded by ingestion")
				.register(registry);
	}

	public List<Shipment> findAll() {
		return repository.findAll();
	}

	public Shipment findById(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment %s not found".formatted(id)));
	}

	public IngestionResult ingest() {
		repository.reset();
		freshness.markIngested();
		int records = repository.count();
		ingestedRecords.increment(records);
		return new IngestionResult(records, freshness.lastIngestion());
	}

	public record IngestionResult(int records, Instant ingestedAt) {
	}

}
