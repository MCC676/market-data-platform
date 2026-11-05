package com.mccanto.mdp.shipment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.mccanto.mdp.quality.DataFreshnessProperties;
import com.mccanto.mdp.quality.DataFreshnessTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentServiceTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

	private final DataFreshnessTracker freshness = new DataFreshnessTracker(new DataFreshnessProperties(),
			new SimpleMeterRegistry(), clock);

	private final ShipmentService service = new ShipmentService(new ShipmentRepository(), freshness,
			new SimpleMeterRegistry());

	@Test
	void listsSeededShipmentsSortedById() {
		assertThat(service.findAll()).extracting(Shipment::id)
				.containsExactly("EM00096929", "EM00100846", "EM00105764", "EM00110021");
	}

	@Test
	void findsShipmentById() {
		assertThat(service.findById("EM00096929").supplier()).isEqualTo("AGROINDUSTRIAS DEL SUR S.A.C.");
	}

	@Test
	void rejectsUnknownShipment() {
		assertThatThrownBy(() -> service.findById("UNKNOWN")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void ingestRefreshesDataFreshness() {
		freshness.markIngested(Instant.parse("2026-11-04T12:00:00Z"));
		assertThat(freshness.isStale()).isTrue();

		ShipmentService.IngestionResult result = service.ingest();

		assertThat(result.records()).isEqualTo(4);
		assertThat(result.ingestedAt()).isEqualTo(clock.instant());
		assertThat(freshness.isStale()).isFalse();
	}

}
