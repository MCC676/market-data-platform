# ADR 0002 — Single repository with service, pipeline and infra

## Status
Accepted (2026-11-05)

## Context
The portfolio must show a complete platform story: an application, its data
pipeline, and its operational tooling. Splitting early would add coordination
cost with no benefit at this scale.

## Decision
Use one repository with independent modules:

- `service/` — Spring Boot API (own Maven build).
- `pipeline/` — Spark + Iceberg job (own Maven build, optional `spark` profile).
- `infra/` — Docker Compose for local observability and Kubernetes manifests.
- `docs/` — architecture, runbook and ADRs.

Each module builds independently; there is no aggregator POM yet.

## Consequences
- Simple local workflow and a single clone for reviewers.
- If the modules ever need shared contracts, add a `contracts/` module rather
  than an aggregator that couples release cycles.
