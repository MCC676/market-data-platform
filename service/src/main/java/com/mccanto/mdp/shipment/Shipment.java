package com.mccanto.mdp.shipment;

import java.math.BigDecimal;
import java.time.Instant;

public record Shipment(
		String id,
		String purchaseOrder,
		String supplier,
		String status,
		BigDecimal amount,
		Instant updatedAt) {
}
