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
3. Trigger ingestion: `curl -X POST http://localhost:8080/api/v1/shipments/ingest`
4. If ingestion itself is the problem, check the pipeline job (Phase 5).
5. Do **not** restart the service to clear the alert; it does not create data.

### MdpHighErrorRate (warning)
5xx rate above 5% for 5 minutes.

1. Check the 5xx breakdown in Grafana: `status`, `uri`, `exception`.
2. Reproduce with the failing `uri`.
3. Roll back the last image tag if the rate started at a deploy.

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
(60m by default) is `NotReady` even though the process is healthy. Trigger
ingestion or restart the deployment to reset the local watermark.

## Known local gotchas

- **Maven Wrapper and HOME=/z/**: this machine sets `HOME=/z/`, so the wrapper
  tries to create `/z/.m2`. Always export
  `MAVEN_USER_HOME="C:/Users/mccanto/.m2"` (Git Bash) before running `./mvnw`.
- **Port 8080 already in use**: stop the previous instance or run with
  `SERVER_PORT=8081`.
