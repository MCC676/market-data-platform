package com.mccanto.mdp.shipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class ShipmentRepository {

	private final Map<String, Shipment> shipments = new ConcurrentHashMap<>();

	public ShipmentRepository() {
		reset();
	}

	public void reset() {
		Instant now = Instant.now();
		shipments.clear();
		save(new Shipment("EM00096929", "OC-4500012345", "AGROINDUSTRIAS DEL SUR S.A.C.", "IN_TRANSIT",
				new BigDecimal("20278.77"), now.minusSeconds(900)));
		save(new Shipment("EM00100846", "OC-4500021987", "TRANSPORTES DEL PACIFICO S.A.", "DELIVERED",
				new BigDecimal("15430.50"), now.minusSeconds(5400)));
		save(new Shipment("EM00105764", "OC-4500030771", "SERVICIOS INDUSTRIALES LIMA S.A.C.", "PENDING_DOCS",
				new BigDecimal("8790.00"), now.minusSeconds(10800)));
		save(new Shipment("EM00110021", "OC-4500036540", "INVERSIONES ANDINAS E.I.R.L.", "IN_TRANSIT",
				new BigDecimal("31250.99"), now.minusSeconds(1800)));
	}

	private void save(Shipment shipment) {
		shipments.put(shipment.id(), shipment);
	}

	public List<Shipment> findAll() {
		return shipments.values().stream()
				.sorted(Comparator.comparing(Shipment::id))
				.toList();
	}

	public Optional<Shipment> findById(String id) {
		return Optional.ofNullable(shipments.get(id));
	}

	public int count() {
		return shipments.size();
	}

}
