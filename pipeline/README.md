# mdp-pipeline

Spark + Iceberg ingestion job (Phase 5).

## Data flow

```
extracts/shipments_extract.csv (EXACTUS-like)
        │
        ▼
bronze.shipments_raw          append-only, as landed, partitioned by day(ingested_at)
        │  reject null ids and unknown statuses, dedup by id (latest updated_at wins)
        ▼
silver.shipments              one row per shipment, MERGE INTO by id
        │  full rebuild
        ▼
gold.shipment_daily_metrics   count and amount per day and status
```

Each successful run also writes:

- `warehouse/_watermarks/shipments.json` — shared freshness watermark read by the API.
- `warehouse/_quality/latest.json` — extracted / rejected / duplicate / invalid-status counts.

## Design rules

- **Idempotent silver**: `MERGE INTO` on `id`, only updating when `updated_at` is newer.
- **Append-only bronze**: re-runs grow bronze (it is the audit trail); silver does not duplicate.
- **Rebuildable gold**: `INSERT OVERWRITE` from silver on every run.
- Rejected rows (null/blank id) and rows with an unknown status stay in bronze but
  never reach silver; both counts are reported separately.

## Running

The job needs Spark 4.x, which does not officially support the local JDK 23, so it
runs inside the official Spark image (Linux, Java 21):

```bash
./pipeline/run-docker.sh
```

The script builds `target/mdp-pipeline.jar` with the `spark` Maven profile
(Spark is `provided`, the image supplies it) and runs `spark-submit` with
`--packages org.apache.iceberg:iceberg-spark-runtime-4.1_2.13:1.11.0`.
The first run downloads the image and Iceberg (~700 MB + ~50 MB); the Ivy cache
is kept in the `mdp-ivy-cache` Docker volume.

Overrides: `EXTRACT` (path inside the container), `SPARK_MASTER`, `SPARK_IMAGE`,
`ICEBERG_VERSION`.

## Expected output

On the default extract (7 rows, 1 duplicated id):

```
[mdp] extracted=7 rejected=0 duplicates=1 unique=6 invalidStatus=0 bronze=7 silver=6 gold=5
```

Run it twice: `bronze` grows, `silver` stays at 6, `gold` stays at 5. That is the
idempotency check.

To exercise the quality gate, run the invalid extract (unknown status + missing id):

```bash
EXTRACT=/opt/mdp/extracts/shipments_extract_invalid.csv ./pipeline/run-docker.sh
```

It reports `rejected=1 invalidStatus=1` and adds nothing to silver; the next
normal run resets both counters to 0.

## Inspecting the lakehouse

```bash
docker run --rm -it --user root \
  -v "$PWD/pipeline:/opt/mdp" -w /opt/mdp \
  apache/spark:4.1.2-scala2.13-java21-python3-ubuntu \
  /opt/spark/bin/spark-sql \
    --packages org.apache.iceberg:iceberg-spark-runtime-4.1_2.13:1.11.0 \
    --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions \
    --conf spark.sql.catalog.mdp=org.apache.iceberg.spark.SparkCatalog \
    --conf spark.sql.catalog.mdp.type=hadoop \
    --conf spark.sql.catalog.mdp.warehouse=file:/opt/mdp/warehouse \
    -e "SELECT * FROM mdp.silver.shipments ORDER BY id"
```

## Status

| Stage | Status |
|---|---|
| Bronze ingestion | Done |
| Silver merge | Done |
| Gold aggregation | Done |
| Quality report | Done (counts written to `_quality/latest.json`) |
| Shared freshness watermark | Done (consumed by the service) |
| MinIO/S3 wiring | Pending |

## Limitations

- The hadoop catalog writes to the container's mounted filesystem; S3/MinIO is the
  next step for a real cluster.
- `spark-submit` runs in `local[2]`; a Kubernetes SparkApplication/Job manifest is
  not included yet.
- The API still serves its in-memory dataset; switching reads to silver is the next
  phase.
