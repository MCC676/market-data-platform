#!/usr/bin/env bash
# End-to-end demo of the local stack. Requires the API running (compose or
# spring-boot:run) and Docker for the pipeline.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
POLL_WAIT="${POLL_WAIT:-35}"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

step "Waiting for the API at $BASE"
for i in $(seq 1 60); do
	if curl -sf "$BASE/actuator/health/readiness" >/dev/null 2>&1; then
		break
	fi
	sleep 2
done

step "1. Shipments served by the API"
curl -s "$BASE/api/v1/shipments"; echo

step "2. Readiness before this run (freshness comes from the shared watermark)"
curl -s "$BASE/actuator/health/readiness"; echo

step "3. Running the Spark + Iceberg pipeline (bronze -> silver -> gold)"
"$ROOT/pipeline/run-docker.sh" 2>&1 | grep -E '^\[mdp\]'

step "4. Waiting ${POLL_WAIT}s for the watermark/quality pollers"
sleep "$POLL_WAIT"

step "5. Freshness after the pipeline"
curl -s "$BASE/actuator/health/readiness"; echo
curl -s "$BASE/actuator/prometheus" | grep -E '^mdp_data_(lag_seconds|last_ingestion)'

step "6. Quality gauges from the last report"
curl -s "$BASE/actuator/prometheus" | grep -E '^mdp_quality_' | sort

step "7. Quality gate: unknown status + missing id"
EXTRACT=/opt/mdp/extracts/shipments_extract_invalid.csv "$ROOT/pipeline/run-docker.sh" 2>&1 | grep -E '^\[mdp\]'
sleep "$POLL_WAIT"
curl -s "$BASE/actuator/prometheus" | grep -E '^mdp_quality_(rejected|invalid_status|silver)_rows' | sort

step "8. Clean run resets the quality counters"
"$ROOT/pipeline/run-docker.sh" 2>&1 | grep -E '^\[mdp\]'
sleep "$POLL_WAIT"
curl -s "$BASE/actuator/prometheus" | grep -E '^mdp_quality_(rejected|invalid_status|silver)_rows' | sort

step "Demo complete"
echo "Prometheus: http://localhost:9090   Grafana: http://localhost:3000 (admin/admin)"
