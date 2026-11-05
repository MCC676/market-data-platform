package com.mccanto.mdp.shipment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

	private final ShipmentService service;

	public ShipmentController(ShipmentService service) {
		this.service = service;
	}

	@GetMapping
	public List<Shipment> list() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public Shipment get(@PathVariable String id) {
		return service.findById(id);
	}

	@PostMapping("/ingest")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ShipmentService.IngestionResult ingest() {
		return service.ingest();
	}

}
