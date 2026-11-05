# ADR 0003 — Data freshness affects readiness, never liveness

## Status
Accepted (2026-11-05)

## Context
Data observability usually surfaces as a dashboard or a batch alert. That is too
late: a service serving stale data is functionally broken even if the process is
healthy. But a stale dataset must not cause Kubernetes to restart the pod —
restarting does not create fresh data.

## Decision
- Expose `dataFreshness` as a **health contributor**.
- Include it **only in the readiness group** (`/actuator/health/readiness`).
- Keep liveness limited to `livenessState` and `ping`.

When data is stale, Kubernetes removes the pod from Service endpoints (503 on
readiness) but leaves the process running, so it can resume instantly once
ingestion succeeds.

## Consequences
- Stale data degrades availability by design.
- The threshold is configurable (`mdp.data.freshness.threshold`, default `60m`).
- The same signal feeds Prometheus (`mdp_data_lag_seconds`) and the `MdpDataStale`
  alert, so dashboards, probes and alerting agree on one definition.
