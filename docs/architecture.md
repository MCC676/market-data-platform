# Architecture

## Context

```
                 ┌──────────────────────────────┐
                 │        API clients           │
                 └──────────────┬───────────────┘
                                │ HTTP / JSON
                 ┌──────────────▼───────────────┐
                 │  service/ (Spring Boot 4)    │
                 │  /api/v1/shipments           │
                 │  /actuator/health|prometheus │
                 └───────┬──────────────┬───────┘
                         │              │
              ┌──────────▼───┐   ┌──────▼─────────┐
              │  ingestion   │   │  observability │
              │  (Phase 5)   │   │  scrape 15s    │
              └──────┬───────┘   └──────┬─────────┘
                     │                  │
        ┌────────────▼──────┐   ┌───────▼────────┐
        │ Spark + Iceberg   │   │  Prometheus    │
        │ bronze/silver/gold│   │  Alertmanager  │
        └───────────────────┘   │  Grafana       │
                                └────────────────┘
```

## Service

- Stateless Spring Boot API. All state lives in the (future) lakehouse.
- `ShipmentRepository` is an in-memory placeholder seeded with EXACTUS-like data;
  it exists to keep Phase 1 runnable without a database.
- `POST /api/v1/shipments/ingest` simulates an ingestion cycle: it reloads the
  dataset and refreshes the freshness tracker. Phase 5 replaces it with a read
  from the Iceberg silver table.

## Observability contract

| Signal | Source | Consumer |
|---|---|---|
| `http_server_requests_seconds_*` | Micrometer HTTP instrumentation | Grafana, error-rate alert |
| `mdp_data_lag_seconds` | `DataFreshnessTracker` gauge | Grafana, `MdpDataStale` alert |
| `mdp_data_last_ingestion_timestamp_seconds` | gauge | PromQL `time() - metric` |
| `mdp_ingest_records_total` | ingestion counter | Grafana |
| `/actuator/health/liveness` | `livenessState`, `ping` | Kubernetes liveness probe |
| `/actuator/health/readiness` | `readinessState`, `dataFreshness` | Kubernetes readiness probe |

## Health semantics

Data freshness is a **readiness** concern (see ADR 0003). With stale data:

- `readiness` → 503 (pod removed from endpoints)
- `liveness` → 200 (pod is not restarted; a restart cannot fix missing data)

Verified locally: a second instance started with
`MDP_DATA_FRESHNESS_THRESHOLD=0s` returned 503 on readiness and 200 on liveness.

Verified on kind (2026-11-05): after ~70 minutes without ingestion, both pods
reported `Running 0/1` with **0 restarts** and the Service had no endpoints. A
single `POST /api/v1/shipments/ingest` restored readiness to 200 and the pod was
added back to the endpoints.

### Known limitation

Freshness is currently **per-pod state**: each replica has its own
`DataFreshnessTracker`, so ingesting through one pod does not refresh the other.
This is acceptable for a scaffold, but Phase 5 must derive freshness from the
shared silver table (or a shared ingestion watermark) so every replica observes
the same truth.

## Roadmap to the lakehouse

1. Land the EXACTUS extract in `bronze.shipments_raw` (append-only).
2. Merge into `silver.shipments` keyed by shipment id (idempotent).
3. Build `gold.shipment_daily_metrics`.
4. Emit quality metrics from the job and expose them to Prometheus.
5. Point the service at silver; `ingest` becomes a real pipeline trigger.
