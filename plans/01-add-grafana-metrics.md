# Plan: Add Grafana Metrics

This plan is split into two parts:

- **Part 1 — Local:** add a full observability stack to `docker-compose.yml` so database and
  container metrics are visible on your machine while developing.
- **Part 2 — AWS:** what to add on top of that so the same metrics are visible in a
  production Grafana instance running in AWS.

The `docker-compose.yml` is used **locally only**. It is not deployed to any environment.

---

## Decision note: OpenSearch vs Prometheus + Grafana

OpenSearch is a search and analytics engine — it is **not** a suitable replacement for
Prometheus + Grafana for time-series metrics:

| Capability | Prometheus + Grafana | OpenSearch + Dashboards |
|---|---|---|
| Scrape `/actuator/prometheus` | ✅ native | ❌ requires Data Prepper pipeline |
| Store time-series metrics efficiently | ✅ TSDB | ⚠️ possible but heavyweight |
| Visualise with PromQL | ✅ native | ❌ no PromQL support |
| Import community dashboards by ID | ✅ grafana.com | ❌ no equivalent library |
| Memory footprint (local dev) | ~200 MB total | ~1–2 GB (JVM-based) |

**Use Prometheus + Grafana for metrics.** OpenSearch is the right tool for log search and
APM traces — those are separate concerns and the two stacks can run alongside each other
without conflict.

---

## Table of Contents

- **Part 1 — Local**
  - [Overview](#overview)
  - [Step 1 – Instrument the API](#step-1--instrument-the-api)
  - [Step 2 – Add the Postgres monitoring role](#step-2--add-the-postgres-monitoring-role)
  - [Step 3 – Create the Prometheus config](#step-3--create-the-prometheus-config)
  - [Step 4 – Create the Grafana provisioning config](#step-4--create-the-grafana-provisioning-config)
  - [Step 5 – Update docker-compose.yml](#step-5--update-docker-composeyml)
  - [Step 6 – Import and commit the dashboards](#step-6--import-and-commit-the-dashboards)
  - [Step 7 – Verify locally](#step-7--verify-locally)
  - [Local file map](#local-file-map)
- **Part 2 — AWS**
  - [AWS Architecture](#aws-architecture)
  - [Step A – Provision AMP and AMG in AWS](#step-a--provision-amp-and-amg-in-aws)
  - [Step B – Add a ServiceMonitor to the Helm chart](#step-b--add-a-servicemonitor-to-the-helm-chart)
  - [Step C – Configure remote_write to AMP](#step-c--configure-remote_write-to-amp)
  - [Step D – Connect AMG to AMP and import dashboards](#step-d--connect-amg-to-amp-and-import-dashboards)
  - [IAM permissions summary](#iam-permissions-summary)
  - [AWS file map](#aws-file-map)

---

# Part 1 — Local

## Overview

`docker-compose.yml` currently runs only Postgres. After this plan it will also run:

```
┌───────────────────────────────────────────────────┐
│  docker-compose (local only)                      │
│                                                   │
│  ┌───────────┐  scrapes  ┌─────────────────────┐  │
│  │  API      │◄──────────│                     │  │
│  │  :8080    │           │     Prometheus      │  │
│  │  /actuator│           │     :9090           │  │
│  │  /prometh…│           │                     │  │
│  └───────────┘           │                     │  │
│                          │                     │  │
│  ┌───────────┐  scrapes  │                     │  │
│  │ postgres  │◄──────────│                     │  │
│  │ -exporter │           │                     │  │
│  │  :9187    │           └──────────┬──────────┘  │
│  └───────────┘                      │             │
│                                     │ queries     │
│  ┌───────────┐  scrapes             │             │
│  │ cAdvisor  │◄─────────────────────┘             │
│  │  :8081    │                                    │
│  └───────────┘            ┌─────────────────────┐ │
│                           │     Grafana         │ │
│  ┌───────────┐            │     :3000           │ │
│  │ Postgres  │            └─────────────────────┘ │
│  │  :5432    │                                    │
│  └───────────┘                                    │
└───────────────────────────────────────────────────┘
```

| New service | Image | Purpose |
|---|---|---|
| `prometheus` | `prom/prometheus:v3.3.0` | Scrapes and stores time-series metrics |
| `grafana` | `grafana/grafana:11.6.0` | Visualises metrics; dashboards auto-provisioned from files |
| `postgres-exporter` | `prometheuscommunity/postgres-exporter:v0.17.1` | Exports Postgres internals as Prometheus metrics |
| `cadvisor` | `gcr.io/cadvisor/cadvisor:v0.52.1` | Exports Docker container CPU/memory/network metrics |

---

## Step 1 – Instrument the API

The API already has Spring Boot Actuator. Two small changes wire it up to Prometheus.

### 1a. Add the Micrometer Prometheus registry

In `data-access-service/build.gradle`, inside the `dependencies` block:

```groovy
// Exposes /actuator/prometheus — picked up automatically by Spring Boot Actuator
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

### 1b. Expose the prometheus endpoint

In `data-access-service/src/main/resources/application.yml`, update the `management` block:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
  info:
    env:
      enabled: true
```

> **Security note:** `/actuator/prometheus` contains no user data. It is safe on the local
> compose network. In production it is only reachable by the in-cluster Prometheus scraper —
> not exposed via the public ingress.

---

## Step 2 – Add the Postgres monitoring role

`postgres-exporter` needs a read-only database role and the `pg_stat_statements` extension to
report query-level metrics.

Create `data-access-service/src/main/resources/db/migration/V999__monitoring_setup.sql`:

```sql
-- Enable query stats collection
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Read-only monitoring role for postgres-exporter
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monitoring') THEN
    CREATE ROLE monitoring WITH LOGIN PASSWORD 'monitoring_password'
      NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END
$$;

GRANT pg_monitor TO monitoring;
```

> Pick a version number (e.g. V999) that does not collide with existing migrations.
>
> This migration also runs via Flyway in production. RDS supports `pg_stat_statements` —
> enable it there via a custom RDS parameter group
> (`shared_preload_libraries = pg_stat_statements`).

---

## Step 3 – Create the Prometheus config

Create `monitoring/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:

  - job_name: laa-data-access-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [ 'laa-api:8080' ]   # matches the service name in docker-compose.yml

  - job_name: postgres
    static_configs:
      - targets: [ 'postgres-exporter:9187' ]

  - job_name: cadvisor
    static_configs:
      - targets: [ 'cadvisor:8080' ]
```

---

## Step 4 – Create the Grafana provisioning config

Grafana's provisioning directory loads data sources and dashboards automatically on container
startup — no manual UI work required.

### Directory layout

```
monitoring/
└── grafana/
    ├── provisioning/
    │   ├── datasources/
    │   │   └── prometheus.yml
    │   └── dashboards/
    │       └── dashboard.yml
    └── dashboards/
        ├── api-metrics.json          ← committed after Step 6
        ├── postgres-metrics.json     ← committed after Step 6
        └── container-metrics.json   ← committed after Step 6
```

### `monitoring/grafana/provisioning/datasources/prometheus.yml`

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

### `monitoring/grafana/provisioning/dashboards/dashboard.yml`

```yaml
apiVersion: 1

providers:
  - name: local-dashboards
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

---

## Step 5 – Update docker-compose.yml

Add the four new services to `docker-compose.yml`. The existing `postgres` service needs a
healthcheck so that dependent services start in the correct order.

```yaml
services:

  postgres:
    image: postgres:17
    container_name: laa-postgres
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: laa_data_access_api
      POSTGRES_USER: laa_user
      POSTGRES_PASSWORD: laa_password
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U laa_user -d laa_data_access_api" ]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - monitoring

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:v0.17.1
    container_name: laa-postgres-exporter
    restart: unless-stopped
    ports:
      - "9187:9187"
    environment:
      DATA_SOURCE_NAME: "postgresql://monitoring:monitoring_password@postgres:5432/laa_data_access_api?sslmode=disable"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - monitoring

  cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.52.1
    container_name: laa-cadvisor
    restart: unless-stopped
    ports:
      - "8081:8080"
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
    networks:
      - monitoring

  prometheus:
    image: prom/prometheus:v3.3.0
    container_name: laa-prometheus
    restart: unless-stopped
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheusdata:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:11.6.0
    container_name: laa-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: /var/lib/grafana/dashboards/api-metrics.json
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro
      - grafanadata:/var/lib/grafana
    depends_on:
      - prometheus
    networks:
      - monitoring

volumes:
  pgdata:
  prometheusdata:
  grafanadata:

networks:
  monitoring:
    driver: bridge
```

---

## Step 6 – Import and commit the dashboards

Rather than hand-authoring hundreds of lines of JSON, import ready-made community dashboards
and commit the exported JSON so they are provisioned automatically on every `docker compose up`.

1. `docker compose up -d`
2. Open http://localhost:3000 (admin / admin)
3. **Dashboards → Import** → enter the ID → **Load** → select **Prometheus** → **Import**
4. **Dashboard settings → JSON Model → Copy to clipboard**
5. Save to `monitoring/grafana/dashboards/<filename>.json` and commit

| Dashboard | Grafana ID | Save as |
|---|---|---|
| Spring Boot 3.x Statistics | `19004` | `api-metrics.json` |
| PostgreSQL Database | `9628` | `postgres-metrics.json` |
| Docker Container & Host Metrics (cAdvisor) | `14282` | `container-metrics.json` |

### What each dashboard shows

#### `api-metrics.json` — Spring Boot / JVM

| Panel | Metric |
|---|---|
| HTTP request rate | `rate(http_server_requests_seconds_count[1m])` |
| HTTP error rate (4xx / 5xx) | `rate(http_server_requests_seconds_count{status=~"4..|5.."}[1m])` |
| HTTP p99 latency | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` |
| JVM heap used | `jvm_memory_used_bytes{area="heap"}` |
| JVM GC pause rate | `rate(jvm_gc_pause_seconds_sum[1m])` |
| Live threads | `jvm_threads_live_threads` |
| HikariCP active connections | `hikari_connections_active` |
| HikariCP pending threads | `hikari_connections_pending` |

#### `postgres-metrics.json` — Postgres

| Panel | Metric |
|---|---|
| Active connections | `pg_stat_activity_count{state="active"}` |
| Transactions per second | `rate(pg_stat_database_xact_commit[1m]) + rate(pg_stat_database_xact_rollback[1m])` |
| Cache hit ratio | `pg_stat_database_blks_hit / (pg_stat_database_blks_hit + pg_stat_database_blks_read)` |
| Deadlocks | `rate(pg_stat_database_deadlocks[5m])` |
| Slow queries (top 10) | `topk(10, pg_stat_statements_mean_exec_time_seconds)` |
| DB size | `pg_database_size_bytes` |

#### `container-metrics.json` — cAdvisor

| Panel | Metric |
|---|---|
| Container CPU % | `rate(container_cpu_usage_seconds_total[1m]) * 100` |
| Container memory usage | `container_memory_usage_bytes` |
| Network RX / TX rate | `rate(container_network_receive_bytes_total[1m])` / `rate(container_network_transmit_bytes_total[1m])` |

---

## Step 7 – Verify locally

```bash
# Start the full stack
docker compose up -d

# All 5 services should be Up / healthy
docker compose ps

# Prometheus targets should all be UP
open http://localhost:9090/targets

# Grafana should load and show the default dashboard
open http://localhost:3000

# API is emitting metrics
curl http://localhost:8080/actuator/prometheus | head -20

# Postgres exporter is up
curl http://localhost:9187/metrics | grep pg_up
```

Expected:

| Check | Expected result |
|---|---|
| `docker compose ps` | All 5 services `Up` |
| Prometheus targets | All 3 targets `UP` |
| Grafana home | Default API dashboard loads |
| `pg_up` | `pg_up 1` |

---

## Local file map

| File | Action |
|---|---|
| `docker-compose.yml` | Modify — add healthcheck to postgres; add postgres-exporter, cadvisor, prometheus, grafana |
| `data-access-service/build.gradle` | Modify — add `micrometer-registry-prometheus` dependency |
| `data-access-service/src/main/resources/application.yml` | Modify — expose `prometheus` actuator endpoint |
| `data-access-service/src/main/resources/db/migration/V999__monitoring_setup.sql` | Create — monitoring role + pg_stat_statements extension |
| `monitoring/prometheus/prometheus.yml` | Create — Prometheus scrape config |
| `monitoring/grafana/provisioning/datasources/prometheus.yml` | Create — Grafana data source pointing at Prometheus |
| `monitoring/grafana/provisioning/dashboards/dashboard.yml` | Create — Grafana dashboard folder provider |
| `monitoring/grafana/dashboards/api-metrics.json` | Create — import from Grafana ID 19004, then commit |
| `monitoring/grafana/dashboards/postgres-metrics.json` | Create — import from Grafana ID 9628, then commit |
| `monitoring/grafana/dashboards/container-metrics.json` | Create — import from Grafana ID 14282, then commit |

---

# Part 2 — AWS

In production the API runs on EKS, deployed via Helm and GitHub Actions. Docker Compose is not
involved. The AWS observability stack replaces the local Prometheus + Grafana containers with
two managed AWS services: **Amazon Managed Service for Prometheus (AMP)** and **Amazon Managed
Grafana (AMG)**.

## AWS Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  AWS                                                           │
│                                                                │
│  ┌──────────────────────────────┐                              │
│  │  EKS cluster                 │                              │
│  │                              │                              │
│  │  ┌────────────────────────┐  │                              │
│  │  │  laa-data-access-api   │  │                              │
│  │  │  pod :8080             │  │                              │
│  │  │  /actuator/prometheus  │  │                              │
│  │  └───────────┬────────────┘  │                              │
│  │              │ ServiceMonitor │                              │
│  │              ▼               │                              │
│  │  ┌────────────────────────┐  │  remote_write  ┌──────────┐  │
│  │  │  kube-prometheus-stack │──┼────────────────►   AMP    │  │
│  │  │  (Prometheus +         │  │                └────┬─────┘  │
│  │  │   Alertmanager)        │  │                     │        │
│  │  └────────────────────────┘  │              queries│        │
│  │                              │                     ▼        │
│  └──────────────────────────────┘              ┌──────────┐    │
│                                                │   AMG    │    │
│                                                │ Grafana  │    │
│                                                │ (managed)│    │
│                                                └──────────┘    │
└────────────────────────────────────────────────────────────────┘
```

| AWS service | Purpose |
|---|---|
| **Amazon Managed Service for Prometheus (AMP)** | Prometheus-compatible TSDB. Receives metrics via `remote_write` from the in-cluster Prometheus. |
| **Amazon Managed Grafana (AMG)** | Fully managed Grafana. Queries AMP. Access controlled via IAM Identity Center (SSO). |
| **kube-prometheus-stack** (Helm, existing in cluster) | In-cluster Prometheus + Alertmanager. Discovers the API pod via a `ServiceMonitor`. |

There is no Grafana pod running in the cluster and no long-term metric storage in the cluster —
AMP handles retention.

---

## Step A – Provision AMP and AMG in AWS

### Create an AMP workspace

```bash
aws amp create-workspace --alias laa-data-access-api --region <AWS_REGION>
```

Note the `workspaceId` from the output — it is needed in Steps C and D.

### Create an AMG workspace

Via the AWS Console or Terraform:

1. Go to **Amazon Managed Grafana → Create workspace**, name it `laa-data-access-api`
2. Enable **Amazon Managed Service for Prometheus** as a data source — AMG handles SigV4
   auth automatically, no credentials to manage manually
3. Set authentication to **AWS IAM Identity Center** — developers log in with their existing
   AWS SSO account; no separate Grafana user management required

---

## Step B – Add a ServiceMonitor to the Helm chart

A `ServiceMonitor` is a Kubernetes CRD (from the Prometheus Operator, part of
`kube-prometheus-stack`) that tells in-cluster Prometheus which pods to scrape and how.

Create `.helm/data-access-api/templates/service-monitor.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "data-access-api.fullname" . }}
  labels:
    {{- include "data-access-api.labels" . | nindent 4 }}
    release: kube-prometheus-stack   # must match the Prometheus Operator's serviceMonitorSelector
spec:
  selector:
    matchLabels:
      {{- include "data-access-api.selectorLabels" . | nindent 6 }}
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

> The `release: kube-prometheus-stack` label must match the `serviceMonitorSelector` label
> configured in the cluster's `kube-prometheus-stack` Helm values. Confirm this with the
> platform team.

The `/actuator/prometheus` endpoint from [Step 1](#step-1--instrument-the-api) is the same
code change that serves both local and production — no duplication needed.

---

## Step C – Configure remote_write to AMP

Add a `remote_write` block to the `kube-prometheus-stack` Helm values for each environment
that should ship metrics to AMP. This is the only change needed to the cluster's existing
Prometheus configuration.

```yaml
prometheus:
  prometheusSpec:
    remoteWrite:
      - url: https://aps-workspaces.<AWS_REGION>.amazonaws.com/workspaces/<workspaceId>/api/v1/remote_write
        sigv4:
          region: <AWS_REGION>
        queueConfig:
          maxSamplesPerSend: 1000
          maxShards: 200
          capacity: 2500
```

The Prometheus pod needs an IAM role attached via **IRSA** (IAM Roles for Service Accounts)
with the `aps:RemoteWrite` permission on the AMP workspace ARN.

---

## Step D – Connect AMG to AMP and import dashboards

1. In the AMG workspace, go to **Data sources** — the AMP workspace created in Step A should
   already be listed. Set it as the default data source.

2. Import the same three dashboards used locally:

   | Dashboard | Grafana ID |
   |---|---|
   | Spring Boot 3.x Statistics | `19004` |
   | PostgreSQL Database | `9628` |
   | Docker Container & Host Metrics (cAdvisor) | `14282` |

   Go to **Dashboards → Import**, enter the ID, select AMP as the data source, and import.
   The same PromQL queries used in the local dashboards work in AMG without modification —
   the metric names emitted by the API are identical in both environments.

3. **Postgres metrics in production:** `postgres-exporter` runs as a separate deployment in
   EKS pointing at the RDS instance. The Flyway migration from
   [Step 2](#step-2--add-the-postgres-monitoring-role) that creates the `monitoring` role
   will run against RDS via the normal deployment pipeline. Enable `pg_stat_statements` on
   RDS by adding it to a custom parameter group:
   `shared_preload_libraries = pg_stat_statements`.

---

## IAM permissions summary

| Principal | Permission | Purpose |
|---|---|---|
| Prometheus pod (IRSA) | `aps:RemoteWrite` on AMP workspace ARN | Write metrics to AMP |
| AMG service role | `aps:QueryMetrics`, `aps:GetMetricsSampleData` on AMP workspace ARN | AMG reads from AMP |
| Developers | IAM Identity Center group assignment on AMG workspace | Log in to Grafana |

---

## AWS file map

| File | Action |
|---|---|
| `.helm/data-access-api/templates/service-monitor.yaml` | Create — Prometheus Operator ServiceMonitor so in-cluster Prometheus scrapes the API |
| `kube-prometheus-stack` values file (platform team) | Modify — add `remote_write` block pointing at AMP workspace URL |

