# k6-perf Helm chart

Renders a K6 Operator `TestRun` CR plus its supporting ConfigMap and ServiceAccount,
running the bundled `performance-tests/` script against the data-access-api with
Prometheus remote-write output for Grafana visualisation.

## Prerequisites

- K6 Operator installed in the target cluster (`grafana/k6-operator`)
- Prometheus reachable at the URL set in `values.yaml`
- The bundled script must be present at `.helm/k6-perf/scripts/bundle.js` —
  produced by `cd performance-tests && npm install && npm run build`

## Install

```bash
helm install perf-smoke .helm/k6-perf \
  --namespace laa-data-access-api-uat \
  --set scenario=smoke \
  --set token.existingSecret=k6-perf-bearer
```

## Re-run

K6 Operator does not re-run a completed TestRun. Either delete and reinstall, or
bump a label to force a new resource version.

## Output

Metrics land in Prometheus tagged `testid=data-access-api-perf` and `scenario=<name>`.
Wire those tags into the existing Grafana dashboards.
