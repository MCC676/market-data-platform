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
              │  watermark   │   │  scrape 15s    │
              └──────┬───────┘   └──────┬─────────┘
                     │                  │
        ┌────────────▼──────┐   ┌───────▼────────┐
        │ Spark + Iceberg   │   │  Prometheus    │
        │ bronze/silver/gold│   │  Alertmanager  │
        └───────────────────┘   │  Grafana       │
                                └────────────────┘
```

## Service

- Stateless Spring Boot API. All shared state lives in the lakehouse and the
  watermark file.
- `ShipmentRepository` is an in-memory placeholder seeded with EXACTUS-like data;
  serving silver is still pending.
- `POST /api/v1/shipments/ingest` simulates an ingestion cycle for local demos.
- `SilverWatermarkPoller` polls `_watermarks/shipments.json` every 30 s and moves
  the freshness tracker forward, so every replica observes the same ingestion
  time instead of its own memory.

## Pipeline

`pipeline/` runs Spark 4.1.2 + Iceberg 1.11.0 inside the official Spark image
(the local JDK 23 is not supported by Spark 4.x):

| Table | Write mode | Key |
|---|---|---|
| `mdp.bronze.shipments_raw` | append | raw extract + `ingested_at`, partitioned by day |
| `mdp.silver.shipments` | `MERGE INTO` | `id`, updates only when `updated_at` is newer |
| `mdp.gold.shipment_daily_metrics` | `INSERT OVERWRITE` | derived per day and status |

A run also writes `_quality/latest.json` (extracted, rejected, duplicate and
invalid-status counts) and `_watermarks/shipments.json` on success.

## Observability contract

| Signal | Source | Consumer |
|---|---|---|
| `http_server_requests_seconds_*` | Micrometer HTTP instrumentation | Grafana, error-rate alert |
| `mdp_data_lag_seconds` | `DataFreshnessTracker` gauge, fed by ingest and the silver watermark | Grafana, `MdpDataStale` alert |
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

### Freshness across replicas

Verified on 2026-11-05: with `MDP_DATA_FRESHNESS_THRESHOLD=0s` the service started
`NotReady` (lag 202 s, liveness 200). Running the pipeline wrote a newer watermark
and, within one poll interval, `lastIngestion` in the readiness details moved from
the service start time to the job's `runStartedAt`. Freshness is therefore shared
state, not per-pod memory.

Remaining limitation: the watermark is a file, so the API container needs the
warehouse mounted (compose does this; the kind manifests do not yet). A future
iteration can read the Iceberg watermark table or a catalog API instead.

## Roadmap to the lakehouse

1. Land the EXACTUS extract in `bronze.shipments_raw` (append-only). **Done.**
2. Merge into `silver.shipments` keyed by shipment id (idempotent). **Done.**
3. Build `gold.shipment_daily_metrics`. **Done.**
4. Emit quality metrics from the job and expose them to Prometheus. **Partial**
   (counts are written to `_quality/latest.json`; no Prometheus exporter yet).
5. Point the service at silver; `ingest` becomes a real pipeline trigger. **Pending.**
