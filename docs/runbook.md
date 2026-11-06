# Runbook

## Alerts

### MdpServiceDown (critical)
Prometheus cannot scrape `app:8080/actuator/prometheus` for 1 minute.

1. `curl -i http://localhost:8080/actuator/health/liveness`
2. Check container logs: `docker compose logs -f app`
3. If the process is alive but scrape fails, check the Prometheus target page
   (`http://localhost:9090/targets`) and the `metrics_path`.

### MdpDataStale (critical)
`mdp_data_lag_seconds` stayed above 3600 for 5 minutes.

1. Confirm the signal:
   `curl -s http://localhost:8080/actuator/health/readiness`
2. The pod should be `NotReady`; traffic must already be routed away.
3. Real path: run the pipeline, which writes a new shared watermark:
   `./pipeline/run-docker.sh`
4. Local-only path: `curl -X POST http://localhost:8080/api/v1/shipments/ingest`
   (does not fix other replicas).
5. If ingestion itself is the problem, check the quality report:
   `cat pipeline/warehouse/_quality/latest.json`.
6. Do **not** restart the service to clear the alert; it does not create data.

### MdpHighErrorRate (warning)
5xx rate above 5% for 5 minutes.

1. Check the 5xx breakdown in Grafana: `status`, `uri`, `exception`.
2. Reproduce with the failing `uri`.
3. Roll back the last image tag if the rate started at a deploy.

### MdpQualityRejectedRows / MdpQualityInvalidStatus (warning)
The last pipeline report contains rows with a missing id or an unknown status.

1. Read the report: `cat pipeline/warehouse/_quality/latest.json`.
2. Find the offending rows in bronze (they are landed but not promoted):
   query `mdp.bronze.shipments_raw` for the run's `ingested_at`.
3. Fix the extract or the upstream mapping, then rerun `./pipeline/run-docker.sh`.
   A clean run resets both gauges to 0 and the alerts resolve.

### MdpQualityEmptySilver (critical)
A report was produced within the last hour but silver has 0 rows.

1. Check that the extract is not empty and that the report's `uniqueRows > 0`.
2. `mdp_quality_rejected_rows` and `invalid_status_rows` will show whether the
   rows were filtered out.
3. Validate the Iceberg tables with `spark-sql` (see `pipeline/README.md`).

## Common operations

```bash
# Full local stack
docker compose -f infra/docker-compose.yml up --build

# Service only
cd service && MAVEN_USER_HOME="$HOME/.m2" ./mvnw spring-boot:run

# Tests
cd service && MAVEN_USER_HOME="$HOME/.m2" ./mvnw verify

# Kubernetes (kind/minikube)
kubectl apply -k infra/k8s
kubectl -n mdp get pods,svc

# Pipeline (Spark + Iceberg in Docker)
./pipeline/run-docker.sh
cat pipeline/warehouse/_quality/latest.json
cat pipeline/warehouse/_watermarks/shipments.json
```

The pipeline takes ~20 s of Spark work after the image is cached. Two consecutive
runs must report the same `silverRows` and `goldRows`; only `bronzeRows` grows.

Quality-gate check (expected: `rejected=1 invalidStatus=1`, silver unchanged):

```bash
EXTRACT=/opt/mdp/extracts/shipments_extract_invalid.csv ./pipeline/run-docker.sh
```

## Local kind cluster

```bash
export KUBECONFIG="C:/Users/mccanto/.kube/config"
kind get clusters
kubectl -n mdp get pods,endpoints
kubectl -n monitoring get pods

# delete everything
kind delete cluster --name mdp
```

If a rollout stalls, check why the readiness probe fails:

```bash
kubectl -n mdp describe pod -l app=mdp-service | tail -30
```

Remember that readiness includes `dataFreshness`: a pod older than the threshold
(60m by default) is `NotReady` even though the process is healthy. Run the pipeline
to publish a new watermark; restarting only resets local state and breaks the
shared-freshness guarantee. The kind manifests do not mount the warehouse yet, so
in-cluster pods fall back to the local ingest watermark.

## Known local gotchas

- **Maven Wrapper and HOME=/z/**: this machine sets `HOME=/z/`, so the wrapper
  tries to create `/z/.m2`. Always export
  `MAVEN_USER_HOME="C:/Users/mccanto/.m2"` (Git Bash) before running `./mvnw`.
- **Port 8080 already in use**: stop the previous instance or run with
  `SERVER_PORT=8081`.
