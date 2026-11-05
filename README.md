# Market Data Platform

A portfolio project that exercises the stack of a senior backend / data platform
role: **Java 21 + Spring Boot** services, **Kubernetes** deployment, **Prometheus /
Grafana** observability, and **data freshness as a runtime signal** — the same idea
as a data-quality check, but wired into health probes and alerting.

> **Status:** Phases 1–4 are complete and verified: the service runs locally and
> on a kind cluster with Prometheus, Alertmanager and Grafana. The Spark + Iceberg
> pipeline is scaffolded (`pipeline/`) and will be implemented in Phase 5.

## What it demonstrates

| Capability | Where |
|---|---|
| Spring Boot 4 REST API, Java records, constructor injection | `service/src/main/java` |
| Micrometer + Actuator + Prometheus registry | `service/pom.xml`, `application.yml` |
| Data freshness metric and health contributor | `service/.../quality/` |
| Liveness vs. readiness semantics (stale data ≠ restart) | ADR 0003, `infra/k8s/deployment.yaml` |
| Multi-stage Docker build, non-root runtime | `service/Dockerfile` |
| Prometheus scrape config, alert rules, Alertmanager | `infra/prometheus/`, `infra/alertmanager/` |
| Grafana provisioning + dashboard as code | `infra/grafana/` |
| Kubernetes probes, resources, ServiceMonitor | `infra/k8s/` |
| CI: build, test, image | `.github/workflows/ci.yml` |
| Lakehouse design (bronze/silver/gold on Iceberg) | `pipeline/README.md` |

## Layout

```
market-data-platform/
├─ service/        Spring Boot API (Maven Wrapper included)
├─ pipeline/       Spark + Iceberg job (scaffold)
├─ infra/          Docker Compose, Prometheus, Alertmanager, Grafana, K8s
├─ docs/           architecture.md, runbook.md, adr/
└─ .github/        CI workflow
```

## Quick start

### Service only

```bash
cd service
MAVEN_USER_HOME="$HOME/.m2" ./mvnw spring-boot:run
```

> **Windows / Git Bash note:** this machine sets `HOME=/z/`. Always export
> `MAVEN_USER_HOME="C:/Users/mccanto/.m2"` before running `./mvnw`, otherwise the
> wrapper tries to create `/z/.m2` and fails with "Permission denied".

### Full observability stack

```bash
cd infra
docker compose up --build
```

| URL | What |
|---|---|
| http://localhost:8080/api/v1/shipments | API |
| http://localhost:8080/actuator/health | Health (details) |
| http://localhost:8080/actuator/prometheus | Metrics |
| http://localhost:9090 | Prometheus |
| http://localhost:9093 | Alertmanager |
| http://localhost:3000 | Grafana (`admin` / `admin`) |

The Grafana dashboard **MDP Overview** is provisioned automatically.

### Kubernetes (kind)

One-shot (cluster + image + monitoring stack + manifests + dashboard):

```bash
./infra/deploy-kind.sh
```

Manual equivalent:

```bash
export KUBECONFIG="$HOME/.kube/config"   # on this machine: C:/Users/mccanto/.kube/config
kind create cluster --name mdp --image kindest/node:v1.31.4
kind load docker-image market-data-platform:local --name mdp
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace --set grafana.adminPassword=admin \
  --set kubeEtcd.enabled=false --set kubeControllerManager.enabled=false \
  --set kubeScheduler.enabled=false --set kubeProxy.enabled=false
kubectl apply -k infra/k8s
```

Access:

```bash
kubectl -n mdp port-forward svc/mdp-service 8080:80
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090
```

> **Windows note:** `kind` also writes its kubeconfig under `$HOME`. With
> `HOME=/z/` you must export `KUBECONFIG` to the real profile first, otherwise
> cluster creation fails at the end with `mkdir Z:\.kube: Access is denied`.

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/shipments` | List shipments (sorted by id) |
| `GET` | `/api/v1/shipments/{id}` | Get one shipment (404 if unknown) |
| `POST` | `/api/v1/shipments/ingest` | Simulate an ingestion cycle (202) |

## Observability contract

| Metric | Type | Meaning |
|---|---|---|
| `mdp_data_lag_seconds` | gauge | Seconds since the last successful ingestion |
| `mdp_data_last_ingestion_timestamp_seconds` | gauge | Epoch seconds of the last ingestion |
| `mdp_ingest_records_total` | counter | Total records loaded by ingestion |
| `http_server_requests_seconds_*` | histogram | HTTP latency/rate by `uri`, `status` |

Alerts (`infra/prometheus/alerts.yml`):

- `MdpServiceDown` — Prometheus cannot scrape the service for 1 minute.
- `MdpDataStale` — `mdp_data_lag_seconds > 3600` for 5 minutes.
- `MdpHighErrorRate` — 5xx rate above 5% for 5 minutes.

### Health semantics

```
/actuator/health/liveness   -> livenessState, ping          (process is alive)
/actuator/health/readiness  -> readinessState, dataFreshness (safe to receive traffic)
```

When data is stale, readiness returns **503** and Kubernetes removes the pod from
the Service endpoints; liveness stays **200**, so the pod is not restarted
(a restart cannot create data). This was verified locally with
`MDP_DATA_FRESHNESS_THRESHOLD=0s`.

## Tests

```bash
cd service
MAVEN_USER_HOME="$HOME/.m2" ./mvnw verify
```

7 tests: context load, shipment service behaviour, and the freshness health
contributor (UP when fresh, DOWN when stale).

## Configuration

| Property | Env var | Default |
|---|---|---|
| `mdp.data.freshness.threshold` | `MDP_DATA_FRESHNESS_THRESHOLD` | `60m` |
| `server.port` | `SERVER_PORT` | `8080` |

## Roadmap

| Phase | Deliverable | Status |
|---|---|---|
| F1 | Spring Boot service, domain, tests | Done |
| F2 | Docker + CI | Done (image built and loaded into kind) |
| F3 | Metrics, Actuator, Grafana dashboard | Done |
| F4 | Kubernetes + ServiceMonitor + PrometheusRule | Done (kind, verified end-to-end) |
| F5 | Spark + Iceberg pipeline (bronze/silver/gold) | Scaffolded |
| F6 | Automated data-quality checks + alerts | Partial (`dataFreshness`) |
| F7 | ADRs, runbook, English demo | Docs done, demo pending |

## Design decisions

See `docs/adr/`:

- ADR 0001 — Java 21 and Spring Boot 4
- ADR 0002 — Single repository with service, pipeline and infra
- ADR 0003 — Data freshness affects readiness, never liveness
