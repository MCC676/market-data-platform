# ADR 0004 — Quality failures alert, they do not affect readiness

## Status
Accepted (2026-11-05)

## Context
The pipeline now refuses rows with a missing id or an unknown status. A natural
next question is whether a quality failure should also mark the API as `NotReady`,
as stale data does (ADR 0003).

## Decision
Quality counters are exposed as Prometheus gauges and alerts, **not** as health
contributors:

- `mdp_quality_rejected_rows` — rows rejected for a missing id.
- `mdp_quality_invalid_status_rows` — rows with a status outside the allowed set.
- `mdp_quality_silver_rows`, `mdp_quality_gold_rows`, `mdp_quality_bronze_rows`.
- `mdp_quality_report_timestamp_seconds` — when the report was produced.

Alerts (`MdpQualityRejectedRows`, `MdpQualityInvalidStatus`, `MdpQualityEmptySilver`)
fire after 10 minutes so a single bad extract does not page anyone.

## Why
- Readiness answers "can this pod serve traffic?". A rejected row does not stop the
  API from serving the rows it has; taking every pod out of rotation would turn a
  data issue into an outage.
- Freshness is different: stale data means every response is wrong, so refusing
  traffic is correct.
- The metrics still make the problem visible, and the persisted report keeps the
  detail (counts and timestamps) that a gauge alone would lose.

## Consequences
- Quality problems are operator-visible but do not change availability.
- The gauges reflect the **last** pipeline run; a clean run resets them.
- Until the API reads silver, quality counters describe the lakehouse, not the
  in-memory dataset that `/api/v1/shipments` returns.

## Verification
Run the invalid extract:

```bash
EXTRACT=/opt/mdp/extracts/shipments_extract_invalid.csv ./pipeline/run-docker.sh
```

The report and gauges show `rejectedRows=1` and `invalidStatusRows=1`; running the
normal extract afterwards returns both to 0.
