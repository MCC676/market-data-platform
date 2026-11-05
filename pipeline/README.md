# mdp-pipeline

Spark + Iceberg ingestion job. **Scaffolded, implemented in Phase 5.**

## Target data flow

```
CSV/Parquet extract (EXACTUS)
        │
        ▼
bronze.shipments_raw       append-only, as landed
        │  dedup + schema checks
        ▼
silver.shipments           one row per shipment, current state
        │  aggregation
        ▼
gold.shipment_daily_metrics   amount, count, status distribution per day
```

## Design rules

- Every write is **idempotent**: re-running a day must not duplicate rows.
- Bronze keeps the raw payload and the ingestion timestamp (`ingested_at`).
- Silver is keyed by `id` and merged with `MERGE INTO` on Iceberg.
- Gold is derived; it can always be rebuilt from silver.
- Each stage emits quality counters that the service can expose and alert on.

## Running

```bash
# requires the spark profile; downloads ~400 MB on first run
mvn -Pspark -q compile exec:java
```

Versions: Spark 4.1.2 (Scala 2.13), Iceberg 1.11.0.

## Status

| Stage | Status |
|---|---|
| Bronze ingestion | TODO |
| Silver merge | TODO |
| Gold aggregation | TODO |
| Quality checks | TODO |
| MinIO/S3 wiring | TODO |
