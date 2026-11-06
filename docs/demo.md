# Demo guide (English)

A 5–7 minute walkthrough of the platform, plus talking points for an interview.
Everything below was run locally on Windows + Docker Desktop; the pipeline runs in
the official Spark image, the API on JDK 21+.

## What the demo proves

| Minute | Show | Point |
|---|---|---|
| 0:00 | `GET /api/v1/shipments` | Spring Boot 4 API, Java records, constructor injection |
| 0:45 | `GET /actuator/health/readiness` | Freshness is a runtime signal, not a dashboard-only metric |
| 1:30 | `./pipeline/run-docker.sh` | Spark + Iceberg: append-only bronze, idempotent silver MERGE, rebuilt gold |
| 3:00 | `mdp_data_lag_seconds` after the run | The API reads a shared watermark; all replicas agree |
| 3:45 | invalid extract → `mdp_quality_*` gauges | Quality gates and alerting without taking the API offline |
| 5:00 | `kubectl get pods`, Alertmanager, Grafana | Kubernetes probes, ServiceMonitor, PrometheusRule, dashboard as code |
| 6:00 | ADRs and runbook | Decisions and operations, not just code |

## Prerequisites

- Docker Desktop with the daemon running.
- JDK 21+ for the API (`./service/mvnw`), or use the composed image.
- Optional for the Kubernetes part: `kind`, `helm`, `kubectl`.

> Windows note: this machine exports `HOME=/z/`. Export
> `MAVEN_USER_HOME="C:/Users/mccanto/.m2"` before Maven, and
> `KUBECONFIG="C:/Users/mccanto/.kube/config"` before kind.

## Option A — scripted demo (recommended)

Start the API first, then run the script:

```bash
cd service && MAVEN_USER_HOME="C:/Users/mccanto/.m2" ./mvnw spring-boot:run
# in a second terminal, from the repository root:
BASE_URL=http://localhost:8080 ./demo/demo.sh
```

The script walks through: list shipments → readiness → pipeline run → freshness
and quality gauges → invalid extract (quality gate) → clean run (counters reset).

Expected pipeline output on the default extract:

```
[mdp] extracted=7 rejected=0 duplicates=1 unique=6 invalidStatus=0 bronze=... silver=6 gold=5
```

On the invalid extract:

```
[mdp] extracted=2 rejected=1 duplicates=0 unique=0 invalidStatus=1 bronze=... silver=6 gold=5
```

And the gauges after the invalid run:

```
mdp_quality_invalid_status_rows 1.0
mdp_quality_rejected_rows 1.0
mdp_quality_silver_rows 6.0
```

## Option B — full stack with dashboards

```bash
docker compose -f infra/docker-compose.yml up --build -d
./pipeline/run-docker.sh
```

Then open:

- API: http://localhost:8080/api/v1/shipments
- Health: http://localhost:8080/actuator/health
- Prometheus: http://localhost:9090 (try `mdp_data_lag_seconds`, `mdp_quality_rejected_rows`)
- Alertmanager: http://localhost:9093
- Grafana: http://localhost:3000 (`admin` / `admin`), dashboard **MDP Overview**

The compose file mounts `pipeline/warehouse` read-only into the API, so the
watermark and quality report reach the container without a rebuild.

## Option C — Kubernetes

```bash
./infra/deploy-kind.sh
kubectl -n mdp port-forward svc/mdp-service 8080:80
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
```

Demonstrate the stale-data semantics without waiting an hour:

```bash
kubectl -n mdp set env deployment/mdp-service MDP_DATA_FRESHNESS_THRESHOLD=0s
kubectl -n mdp get pods -w
```

Pods become `Running 0/1` with **0 restarts** (readiness 503, liveness 200) and
the Service loses its endpoints. Restore:

```bash
kubectl -n mdp set env deployment/mdp-service MDP_DATA_FRESHNESS_THRESHOLD=60m
```

The kind manifests do not mount the warehouse yet, so in-cluster freshness falls
back to the local ingest endpoint. This limitation is documented in
`docs/architecture.md`.

## Talking points

**Why freshness is readiness, not liveness (ADR 0003).** A stale dataset makes
every response wrong, so the pod should stop receiving traffic. But restarting
cannot create data, so it must not be restarted. Liveness stays green.

**Why quality failures alert instead of affecting readiness (ADR 0004).** One bad
row does not justify removing every replica from rotation. The gauges and alerts
keep the problem visible while the API continues serving good rows.

**Why silver is a MERGE and bronze is append-only.** Bronze is the replayable
audit trail of what landed. Silver is current state keyed by shipment id, and
`MERGE INTO` with an `updated_at` guard makes re-runs idempotent. Gold is always
rebuildable from silver.

**How the demo maps to the job description.**
- Java/Spring: Spring Boot 4, Java records, health contributors, Micrometer.
- Kubernetes: probes with different semantics, resources, ServiceMonitor,
  PrometheusRule, kustomize, kind.
- Lakehouse: Apache Iceberg tables, partitioning, MERGE, time travel available
  through the catalog.
- Data observability: freshness, volume and quality signals wired into Prometheus
  and Alertmanager, with alert runbooks.
- Operations: ADRs, runbook, CI, reproducible local environment.

**What I would change at scale.** S3/MinIO instead of the hadoop catalog, a REST
catalog (Nessie/Glue) for multi-writer safety, Spark on Kubernetes via the
operator, an OpenLineage emitter for lineage, and serving silver through Trino
instead of an in-memory repository.

## Known limitations (be explicit)

- The API still serves an in-memory dataset; silver is queried through `spark-sql`.
- The watermark and quality report are files: compose mounts them, kind does not.
- Spark runs in `local[2]`; there is no in-cluster job manifest yet.
- `MdpQualityEmptySilver` only fires when a fresh report exists.
