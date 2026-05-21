# Helm RC Values Configuration

**Technical Reference for Release Candidate Helm Deployment**  
**Last Updated**: 21 May 2026

---

## Overview

This document explains the Helm values configuration for the RC environment. The RC uses environment-specific values to define resources, scaling, monitoring, and networking.

**File location**: `.helm/data-access-api/values/rc.yaml`

---

## Values Configuration

### Replica Count

```yaml
replicaCount: 2
```

**Explanation**:
- Number of initial application pods to run
- RC runs 2 by default (higher stability than preview branches' 1 pod)
- HPA will scale this up/down based on load

**When to change**:
- If RC experiences high load immediately: Increase to 3 or 4
- If RC underutilized: Can decrease to 1 (but 2 recommended for availability)

---

### Image Configuration

```yaml
image:
  repository: null  # Set by GitHub Actions
  tag: null         # Set by GitHub Actions (git SHA)
```

**Explanation**:
- `repository`: ECR registry URL (set by deploy job, not in values file)
- `tag`: Docker image tag (set to git commit SHA by GitHub Actions)

**How it works**:
```bash
# GitHub Actions passes these at deployment time:
helm upgrade laa-data-access-api-rc ... \
  --set image.repository="[ECR]/[repo]" \
  --set image.tag="[git-sha]" \
```

---

### Service Account

```yaml
service_account:
  name: laa-data-access-uat-service-account
```

**Explanation**:
- Kubernetes service account RC pods run under
- Grants permissions: ECR access, ConfigMap/Secret access, metrics
- **Reuses UAT service account** (no separate RC SA needed)

**Permissions included**:
- `imagePullSecrets`: Access to ECR repositories
- `ClusterRole`: Prometheus metrics scraping, pod-to-pod communication

---

### Service

```yaml
service:
  type: ClusterIP
  port: 8080
```

**Explanation**:
- `ClusterIP`: Internal service (not directly accessible from outside; ingress routes traffic)
- `port`: Port the service listens on (8080 = Spring app port)

**Why ClusterIP**: Ingress handles external routing; service is internal only

---

### Ingress (External)

```yaml
ingress:
  enabled: true
  className: modsec-non-prod
  annotations:
    external-dns.alpha.kubernetes.io/aws-weight: "100"
    nginx.ingress.kubernetes.io/enable-modsecurity: "true"
    nginx.ingress.kubernetes.io/modsecurity-snippet: |
      SecRuleEngine On
      SecAction "id:900200,phase:1,nolog,pass,t:none,setvar:tx.allowed_methods=GET HEAD POST OPTIONS PUT PATCH DELETE"
      SecDefaultAction "phase:2,pass,log,tag:github_team=laa-data-stewardship-access-team,tag:namespace=laa-data-access-api-uat"
  hosts: []  # Set by GitHub Actions (deployment action)
```

**Explanation**:
- **enabled**: Turn ingress on/off (keep true for RC)
- **className**: `modsec-non-prod` = ModSecurity enabled for non-prod environments
- **annotations**:
  - `external-dns.alpha.kubernetes.io/aws-weight`: Route weight (100 = all traffic here)
  - `nginx.ingress.kubernetes.io/enable-modsecurity`: Enable WAF rules
  - `modsecurity-snippet`: Security rules (allowed HTTP methods, logging)
- **hosts**: Set dynamically by deployment action to `<release-name>-uat.cloud-platform.service.justice.gov.uk`

**How ingress hostname is determined**:
```bash
# deploy_branch action creates ingress with dynamic hostname
ingress_name="laa-data-access-api-rc"
external_host="laa-data-access-api-rc-laa-data-access-api-uat.cloud-platform.service.justice.gov.uk"

# Passed to Helm via:
--set ingress.hosts="{$external_host}"
```

---

### Internal Ingress

```yaml
ingressInternal:
  enabled: true
  className: internal-non-prod
  annotations:
    external-dns.alpha.kubernetes.io/aws-weight: "100"
  hosts: []  # Set by deployment action
```

**Explanation**:
- `className: internal-non-prod`: Internal DNS only (not publicly routable)
- Used by other services inside the cluster to reach RC
- Hostname: `<release-name>-internal-uat.internal-non-prod.cloud-platform.service.justice.gov.uk`

**When used**: Internal systems, monitoring, other microservices

---

### Resource Requests & Limits

```yaml
resources:
  limits:
    cpu: 1000m        # Max CPU per pod (1 core)
    memory: 2G        # Max memory per pod (2 GB)
  requests:
    cpu: 100m         # Baseline CPU reservation
    memory: 2G        # Memory reservation
```

**Explanation**:

| Setting | Meaning | Default | Rationale |
|---------|---------|---------|-----------|
| **limits.cpu** | Hard limit; pod throttled or killed if exceeded | 1000m | Same as UAT; adequate for typical workload |
| **limits.memory** | Hard limit; pod OOMKilled if exceeded | 2G | **Higher than preview branches (1G)** for stability under client load |
| **requests.cpu** | Minimum guaranteed; scheduler reserves this | 100m | **Higher than UAT (25m)**; RC should be responsive |
| **requests.memory** | Minimum guaranteed; scheduler reserves this | 2G | Matches limit; no over-commitment |

**Why RC has higher resources**:
- Clients will load-test RC
- Need stability for multi-client simultaneous testing
- Prevent OOMKill or CPU throttling under realistic client load

**When to adjust**:
- **Increase if**: RC slow under client load, OOMKilled, high CPU throttling
- **Decrease if**: RC underutilized, wasting cluster resources

**Example adjustment**:
```yaml
# For very high load
resources:
  limits:
    cpu: 1500m
    memory: 3G
  requests:
    cpu: 200m
    memory: 3G
```

---

### Autoscaling (HPA)

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 8
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

**Explanation**:

| Setting | Meaning | Why |
|---------|---------|-----|
| **enabled** | Auto-scale pods based on metrics | true = let HPA manage replicas |
| **minReplicas** | Minimum pods (even if idle) | 2 = availability + spare capacity |
| **maxReplicas** | Maximum pods (circuit breaker) | 8 = prevent runaway scaling |
| **targetCPUUtilizationPercentage** | Scale up when CPU >70% | 70% = balance responsiveness vs. resource usage |
| **targetMemoryUtilizationPercentage** | Scale up when memory >80% | 80% = leave headroom before OOMKill |

**How it works**:
1. Prometheus collects CPU/memory metrics
2. HPA compares actual vs. target utilization
3. If CPU >70% → scale up (add more pods, up to max 8)
4. If CPU <50% → scale down (remove pods, down to min 2)
5. Update happens every 15 seconds

**Scaling example**:
```
[Start] 2 pods at 45% CPU
[+5min] Request volume increases
[+8min] CPU at 72% → HPA adds 1 pod → 3 pods
[+10min] CPU at 65% (spread across 3) → stable
[+15min] Clients finish testing
[+18min] CPU at 40% → HPA removes pods down to minReplicas (2)
```

**When to adjust**:
- If scaling happens too slowly: Increase `targetCPUUtilizationPercentage` to 60-65 (more aggressive)
- If scaling happens too fast: Increase targets to 75-80 (more conservative)
- If clients need guaranteed minimum: Keep `minReplicas` at 2 or higher

---

### Sentry (Error Tracking)

```yaml
sentry:
  enabled: true
  environment: rc
  tracesSampleRate: 1
```

**Explanation**:
- **enabled**: Send errors to Sentry
- **environment**: Tag errors as coming from "rc" (vs. "uat", "staging")
- **tracesSampleRate**: 1 = capture 100% of errors (good for RC to catch all issues)

**Why enabled**: Catch unexpected errors clients encounter during testing

**When to disable**: Only if Sentry quota exceeded (very unlikely)

---

### Grafana Dashboard

```yaml
grafana:
  enabled: true
```

**Explanation**:
- Creates a Grafana dashboard ConfigMap specific to RC release
- Dashboard automatically populated by Prometheus metrics
- Scoped to RC release name (doesn't show other releases' metrics)

**Why enabled**: Monitor RC health, performance, errors in real-time

**Dashboard name**: `laa-data-access-api-rc-dashboard`

---

### Slow Query Threshold

```yaml
slowQueryThresholdSeconds: 1
```

**Explanation**:
- SQL queries taking >1 second are logged as "slow"
- Helps identify performance issues during client testing
- Metrics visible in Grafana

**Why 1 second**: Typical for RC; adjust if different performance baseline expected

**When to adjust**:
- If too many false alarms: Increase to 2-3 seconds
- If want stricter monitoring: Decrease to 0.5 seconds

---

### Port Forwarding (Debugging)

```yaml
portForward:
  enabled: true
```

**Explanation**:
- Deploys a port-forward pod in cluster (for RDS debugging in preview branches)
- **For RC**: Usually not needed (uses Bitnami PostgreSQL in same cluster)
- Safe to keep enabled; minimal resource overhead

**Usage**: If you need to access RC database from laptop:
```bash
kubectl port-forward -n laa-data-access-api-uat <port-forward-pod> 5432:5432
```

---

## Spring Profile

```yaml
# Set by deploy_branch action, not in values file
spring.profile: "preview"
```

**Explanation**:
- RC deploys via `deploy_branch`, which determines the Spring profile from the branch name
- For a tag push (`v1.2.3-rc.1`), `GITHUB_REF` is `refs/tags/v1.2.3-rc.1`; the extracted branch name is not `main`
- Any branch name other than `main` causes `deploy_branch` to set `SPRING_PROFILE="preview"` and provision Bitnami PostgreSQL
- `preview` = same security posture as all ephemeral feature branch environments

**Available profiles**:
- `preview`: RC and all ephemeral branch environments (no auth enforcement)
- `unsecured`: UAT main deployment (non-main branches also use `preview`, not this)
- `main`: Staging/production (auth enforced)

**Why `preview` and not `unsecured`**: The `deploy_branch` action branches on `branch_name == "main"` to choose between `unsecured` (for the UAT persistent deployment) and `preview` (for all ephemeral deployments including RC). Since RC tags are not `main`, they always get `preview`.

---

## AWS Region

```yaml
# Set by deployment action, not in values file
aws.region: "eu-west-2"
```

**Explanation**:
- Region for AWS services (ECR, CloudWatch, etc.)
- Set to match deployment environment
- Usually `eu-west-2` (UK/London)

---

## Bitnami PostgreSQL Configuration

Configured in separate file: `.helm/bitnami_postgres/values.yaml`

**RC uses**:
- **Ephemeral storage**: Data lost when RC deleted
- **Automatic provisioning**: `deploy_branch` action sets up PostgreSQL for RC
- **Credentials**: Stored in Kubernetes secret `laa-data-access-api-rc-postgresql`

**Example**:
```yaml
# Implicit in deploy_branch action
helm upgrade laa-data-access-api-rc-postgresql oci://registry-1.docker.io/bitnamicharts/postgresql \
  -f .helm/bitnami_postgres/values.yaml \
  --set auth.password="[random]" \  # Auto-generated
  --namespace laa-data-access-api-uat \
  --install \
  --wait
```

**Accessing database**:
```bash
# Get password
kubectl get secret -n laa-data-access-api-uat laa-data-access-api-rc-postgresql -o jsonpath='{.data.postgres-password}' | base64 -d

# Connect
psql -h <postgresql-svc> -U postgres -d postgres
```

---

## Monitoring & Observability

### Prometheus Labels

Automatically applied by Helm:

```yaml
labels:
  app.kubernetes.io/name: data-access-api
  app.kubernetes.io/instance: laa-data-access-api-rc
  release: laa-data-access-api-rc
```

**Why important**: Grafana dashboards filter by `release=laa-data-access-api-rc` to show only RC metrics

### ServiceMonitor

Automatically created by Helm template:

```yaml
# Prometheus scrapes RC pods on :8080/actuator/prometheus
# Metrics labeled with release=laa-data-access-api-rc
```

**Metrics collected**:
- Request count, latency, errors
- JVM heap, GC activity
- Database connection pool
- Spring Boot metrics

---

## Comparing with Other Environments

| Setting | Preview | UAT | RC | Staging |
|---------|---------|-----|----|----|
| **replicaCount** | 1 | 1 | **2** | 2 |
| **memory.requests** | 1G | 1G | **2G** | 1G |
| **memory.limits** | 1G | 1G | **2G** | 1G |
| **cpu.requests** | 25m | 25m | **100m** | 25m |
| **minReplicas (HPA)** | 1 | 1 | **2** | 2 |
| **maxReplicas (HPA)** | 3 | 5 | **8** | 8 |
| **Database** | **Ephemeral Bitnami** | RDS | **Ephemeral Bitnami** | RDS |
| **Spring profile** | preview | unsecured | **preview** | main |
| **Sentry** | Enabled | Enabled | **Enabled** | Enabled |

**Key differences**:
- RC has **more memory** than UAT/previews (for client load testing)
- RC can scale **higher** than previews (up to 8 replicas)
- RC uses **ephemeral DB** like previews (not persistent like Staging)
- RC **Spring profile `preview`** (same as all ephemeral branch environments; `deploy_branch` sets this for any non-`main` branch name)

---

## Advanced Customization

### Changing Resource Limits

Update `.helm/data-access-api/values/rc.yaml`:

```yaml
resources:
  limits:
    cpu: 1500m         # Increase from 1000m
    memory: 3G         # Increase from 2G
  requests:
    cpu: 200m          # Increase from 100m
    memory: 3G         # Increase from 2G
```

Then redeploy:
```bash
helm upgrade laa-data-access-api-rc .helm/data-access-api \
  -n laa-data-access-api-uat \
  -f .helm/data-access-api/values/rc.yaml \
  --wait
```

### Adjusting Autoscaling

```yaml
autoscaling:
  minReplicas: 3        # Always run 3 pods (higher baseline)
  maxReplicas: 10       # Allow scaling to 10 (more generous)
  targetCPUUtilizationPercentage: 60  # Scale more aggressively
```

### Enabling/Disabling Features

```yaml
# Disable Sentry if quota issues
sentry:
  enabled: false

# Disable Grafana if monitoring not needed (not recommended)
grafana:
  enabled: false

# Disable internal ingress if not needed
ingressInternal:
  enabled: false
```

---

## Verification

### Verify Values Applied

```bash
# Get current values
helm get values laa-data-access-api-rc -n laa-data-access-api-uat

# Get computed values (after merging with defaults)
helm get values laa-data-access-api-rc -n laa-data-access-api-uat --all
```

### Verify Pod Spec

```bash
# See actual pod configuration (including resource limits)
kubectl get pod -n laa-data-access-api-uat <pod-name> -o yaml | grep -A 10 "resources:"
```

### Verify Scaling

```bash
# Check HPA configuration
kubectl get hpa -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc -o yaml
```

---

## Troubleshooting Values

### Pod Not Starting: "Insufficient Memory"

**Cause**: Requested memory exceeds available node resources

**Solution**:
```yaml
resources:
  requests:
    memory: 1G  # Reduce from 2G
  limits:
    memory: 1.5G  # Reduce from 2G
```

### Pod Constantly Restarting: "OOMKilled"

**Cause**: Pod exceeds memory limit

**Solution**:
```yaml
resources:
  limits:
    memory: 3G  # Increase from 2G
```

### RC Not Scaling: HPA Not Activating

**Cause**: Metrics not available to HPA

**Verify**:
```bash
# Check metrics
kubectl top pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# If no metrics, HPA can't function
# Check ServiceMonitor is created:
kubectl get servicemonitor -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
```

---

## Best Practices

1. **Monitor resource utilization**  
   Check Grafana weekly; adjust if consistently >80% or <20%

2. **Keep memory.requests = memory.limits**  
   Prevents pod eviction and ensures predictable performance

3. **Set minReplicas ≥ 2**  
   Ensures availability if one pod fails

4. **Don't set maxReplicas too low**  
   Allows scaling for client load spikes

5. **Use annotations for future operational needs**  
   Document why values are set to current numbers

6. **Test values changes in test environment first**  
   Before applying to production-like Staging

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 21 May 2026 | Platform Team | Initial values documentation |

---

## Related Files

- [RC Workflow Guide](./RC_WORKFLOW.md)
- [RC Operations Runbook](./RC_OPERATIONS.md)
- [Helm Chart](../.helm/data-access-api/Chart.yaml)
- [RC Values File](../.helm/data-access-api/values/rc.yaml) (to be created)
