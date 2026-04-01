# Monitoring: Prometheus & Grafana

This document describes how application metrics are collected, scraped by Prometheus, and visualised in Grafana.

## Architecture overview

```
┌─────────────────────┐      ┌──────────────┐      ┌─────────┐
│  Spring Boot App    │      │  Prometheus  │      │ Grafana │
│                     │◄─────│  (scraper)   │─────►│  (UI)   │
│ /actuator/prometheus│      │              │      │         │
└─────────────────────┘      └──────────────┘      └─────────┘
```

1. The application exposes metrics at `/actuator/prometheus` via Spring Boot Actuator and Micrometer.
2. A **ServiceMonitor** tells Prometheus how and where to scrape.
3. A **NetworkPolicy** allows ingress from the monitoring namespace.
4. A **Grafana dashboard** ConfigMap is deployed per Helm release, scoped to that release's metrics.

## Metrics collected

### HTTP request metrics (out-of-the-box)

Spring Boot automatically records `http_server_requests_seconds` for every HTTP endpoint. This provides:
- Request count, total time, and max time
- Labels: `method`, `uri`, `status`, `outcome`

No custom code is required.

### SQL query metrics

Provided by [datasource-micrometer-spring-boot](https://github.com/jdbc-observations/datasource-micrometer). This auto-configures observation-based tracing of all JDBC queries, emitting `jdbc_query_seconds` metrics.

**Configuration** (`application.yml`):

```yaml
jdbc:
  datasource-proxy:
    slow-query:
      enable-logging: true
      log-level: WARN
      threshold: 0  # seconds; overridden per environment

management:
  metrics:
    distribution:
      percentile-histogram:
        jdbc.query: true  # required for histogram buckets (percentile panels)
```

The `percentile-histogram` setting is critical — without it, `jdbc_query_seconds` is emitted as a summary (no `_bucket` metrics), and histogram-based dashboard panels will be blank.

Slow query thresholds are set per environment in Helm values:

| Environment | Threshold |
|---|---|
| UAT | 0.3s |
| Staging | 0.3s |
| Production | 1s |

### SQL operation type tag

`SqlOperationTypeConvention` (`data-access-service/.../metrics/SqlOperationTypeConvention.java`) adds an `operation_type` low-cardinality tag (`select`, `insert`, `update`, `delete`, `other`) to every `jdbc_query_seconds` observation. This enables the dashboard's "Query Operations Distribution" and "Avg Query Time by Operation" panels.

### JPA entity operation metrics

`EntityOperationMetricsListener` (`data-access-service/.../metrics/EntityOperationMetricsListener.java`) is a Hibernate event listener that records a `jpa.entities` counter with `entity` and `operation` tags. This powers the dashboard's "Entity Operations" panels.

`HibernateMetricsConfig` (`data-access-service/.../config/HibernateMetricsConfig.java`) wires the listener into Hibernate's EventListenerRegistry.

### JVM metrics (out-of-the-box)

Spring Boot Actuator automatically exposes JVM memory, GC, and thread metrics (e.g. `jvm_memory_max_bytes`, `jvm_memory_used_bytes`).

## Kubernetes resources

All Helm templates are in `.helm/data-access-api/templates/`.

### ServiceMonitor (`serviceMonitor.yaml`)

Tells Prometheus to scrape `/actuator/prometheus` every 15 seconds:

```yaml
endpoints:
  - port: http
    interval: 15s
    path: /actuator/prometheus
    relabelings:
      - sourceLabels: [ __meta_kubernetes_pod_label_app_kubernetes_io_name ]
        targetLabel: container
        action: replace
      - sourceLabels: [ __meta_kubernetes_pod_label_app_kubernetes_io_instance ]
        targetLabel: release
        action: replace
```

The second relabeling exposes the Helm release name as a `release` label on all scraped metrics. This is essential for scoping dashboards in shared namespaces (see below).

### NetworkPolicies

See [docs/network-policies.md](network-policies.md) for full details on the network policy that allows Prometheus to scrape metrics.

### Grafana dashboard (`grafana-dashboard.yaml` + `grafana-dashboard-template.json`)

The dashboard is deployed as a ConfigMap with the `grafana_dashboard: ""` label, which Grafana's sidecar automatically discovers.

Each Helm release gets its own dashboard ConfigMap. The dashboard is conditionally created based on `grafana.enabled` in Helm values.

### Per-release scoping

Multiple Helm releases can share the same Kubernetes namespace (e.g. preview branches in UAT). To prevent dashboards from showing metrics from other releases:

- **Application metrics** (HTTP, SQL, JPA, JVM): filtered by `release='{{ .Release.Name }}'` — the label added by the ServiceMonitor relabeling.
- **kube-state-metrics** (pod counts): filtered by `pod=~'{{ .Release.Name }}.*'` — matching pod names that start with the release name.

This means each release's dashboard only shows its own pods and metrics, even in a shared namespace.

## Dashboard panels

The Grafana dashboard includes the following panel groups:

| Group | Metrics used | Key labels |
|---|---|---|
| Pod Info | `kube_pod_container_info`, `kube_pod_status_phase` | `namespace`, `pod`, `container` |
| HTTP Requests | `http_server_requests_seconds_count` | `uri`, `method`, `status` |
| JVM Memory | `jvm_memory_max_bytes`, `jvm_memory_used_bytes` | `area`, `id` |
| SQL Profiling | `jdbc_query_seconds_count`, `jdbc_query_seconds_bucket` | `operation_type` |
| JPA Entities | `jpa_entities_total` | `entity`, `operation` |

## Adding or modifying panels

1. Edit `.helm/data-access-api/templates/grafana-dashboard-template.json`
2. Ensure all new PromQL queries include the appropriate release/pod filter
3. Deploy and verify in UAT before merging

## Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| All panels blank | NetworkPolicy missing or misconfigured | See [network-policies.md](network-policies.md) — check `networkPolicy.yaml` exists and monitoring namespace has `component: monitoring` label |
| SQL histogram panels blank | `percentile-histogram` not enabled | Add `management.metrics.distribution.percentile-histogram.jdbc.query: true` |
| Dashboard shows metrics from other releases | Missing release filter in PromQL | Add `release='{{ .Release.Name }}'` or `pod=~'{{ .Release.Name }}.*'` |
| No `operation_type` breakdown | `SqlOperationTypeConvention` not loaded | Ensure the class is annotated `@Component` and on the component scan path |
