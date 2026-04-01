# Network Policies

This document describes the Kubernetes NetworkPolicy resources deployed by the Helm chart to control traffic to the API and PostgreSQL pods.

All Helm templates are in `.helm/data-access-api/templates/`.

## Overview

```
              NetworkPolicy                          NetworkPolicy
         (allow-prometheus-scraping)            (allow-api-to-postgresql)
              TCP 8080                               TCP 5432
┌──────────────┐      ┌─────────────────────┐      ┌──────────────────┐
│  Prometheus  │─────►│  Spring Boot App    │─────►│   PostgreSQL     │
│  (monitoring │      │                     │      │   (per-release)  │
│   namespace) │      │                     │      │                  │
└──────────────┘      └─────────────────────┘      └──────────────────┘
                              ▲
                              │ TCP 8080
                      ┌───────────────┐
                      │    Ingress    │
                      │  Controllers │
                      │  (namespace) │
                      └───────────────┘
```

In Kubernetes, once any NetworkPolicy selects a pod, all traffic not explicitly allowed is denied. These policies ensure only the expected traffic flows are permitted.

## API NetworkPolicy (`networkPolicy.yaml`)

Allows ingress to the API pods from the monitoring and ingress-controller namespaces, restricted to TCP port 8080.

**Pod selector:** Targets API pods via `app: data-access-api`.

**Ingress rules:**

| Source | Selector | Port |
|---|---|---|
| Prometheus scrapers | Namespace label `component: monitoring` | TCP 8080 |
| Ingress controllers | Namespace label `component: ingress-controllers` | TCP 8080 |

```yaml
spec:
  podSelector:
    matchLabels:
      app: data-access-api
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              component: monitoring
      ports:
        - protocol: TCP
          port: 8080
    - from:
        - namespaceSelector:
            matchLabels:
              component: ingress-controllers
      ports:
        - protocol: TCP
          port: 8080
```

Without this policy, Prometheus cannot scrape metrics (all Grafana dashboard panels will be blank) and external HTTP traffic cannot reach the API.

## PostgreSQL NetworkPolicy (`postgresqlNetworkPolicy.yaml`)

Restricts database access so that only the API pods from the **same Helm release** can reach the PostgreSQL instance on port 5432.

**Pod selector:** Targets the PostgreSQL pod via `app.kubernetes.io/name: postgresql` and `app.kubernetes.io/instance: <release>-postgresql`.

**Ingress rules:**

| Source | Selector | Port |
|---|---|---|
| API pods (same release) | Pod labels `app.kubernetes.io/name: data-access-api` + `app.kubernetes.io/instance: <release>` | TCP 5432 |

```yaml
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: postgresql
      app.kubernetes.io/instance: {{ printf "%s-postgresql" .Release.Name }}
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: data-access-api
              app.kubernetes.io/instance: {{ .Release.Name }}
      ports:
        - protocol: TCP
          port: 5432
```

### Why this is in the API chart (not the PostgreSQL chart)

The PostgreSQL instance is deployed as a separate Helm release using the Bitnami chart. The API chart creates this policy because it knows which pods need database access. This keeps the access rules co-located with the application that depends on them.

### Per-release isolation

This is important in shared namespaces (e.g. UAT with multiple preview branches) — it prevents one release's API pods from connecting to another release's database. The `app.kubernetes.io/instance` label ensures each release's pods can only reach their own PostgreSQL.

## Verifying policies

Check that pod labels match the policy selectors:

```bash
# API pod labels
kubectl get pod -n <namespace> <api-pod> --show-labels

# PostgreSQL pod labels
kubectl get pod -n <namespace> <postgresql-pod> --show-labels
```

Key labels to verify:

| Pod | Required label | Expected value |
|---|---|---|
| API | `app` | `data-access-api` |
| API | `app.kubernetes.io/name` | `data-access-api` |
| API | `app.kubernetes.io/instance` | `<release-name>` |
| PostgreSQL | `app.kubernetes.io/name` | `postgresql` |
| PostgreSQL | `app.kubernetes.io/instance` | `<release-name>-postgresql` |

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| Grafana panels blank | Monitoring NetworkPolicy missing or misconfigured | Check `networkPolicy.yaml` exists and monitoring namespace has `component: monitoring` label |
| API cannot connect to database | PostgreSQL NetworkPolicy missing or label mismatch | Check `postgresqlNetworkPolicy.yaml` exists and pod labels match (use `kubectl get pod --show-labels`) |
| External HTTP requests rejected | Ingress-controller rule missing | Check `networkPolicy.yaml` includes the `ingress-controllers` namespace selector |
