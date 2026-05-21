# RC Operations Runbook

**For**: Internal Team Operating RC Environment  
**Audience**: DevOps, Platform Engineers, On-Call Engineers  
**Last Updated**: 21 May 2026

---

## Quick Reference

| Task | Command | Time | Complexity |
|------|---------|------|-----------|
| **Deploy RC** | `helm upgrade laa-data-access-api-rc ...` | 5-10 min | Low |
| **Monitor RC** | Check Grafana + `kubectl get pods` | 2-5 min | Low |
| **Handle alert** | Check logs, assess severity | 5-15 min | Medium |
| **Rollback RC** | `helm rollback laa-data-access-api-rc` | 2-5 min | Low |
| **Delete RC** | `helm uninstall laa-data-access-api-rc` | 1-2 min | Low |
| **Debug pod** | `kubectl logs`, `kubectl describe` | 5-10 min | Medium |
| **Database issue** | Check PostgreSQL pod, connection | 10-15 min | Medium |

---

## Deployment

### Prerequisites

Before deploying RC, ensure:

```bash
# You have kubectl access to UAT cluster
kubectl cluster-info

# You have helm installed
helm version

# You have ECR credentials configured
aws ecr describe-repositories --region eu-west-2 | grep laa-data-access

# You're on the correct branch
git status
git branch -v | grep "*"  # Should show main or RC branch
```

### Deploy via GitHub Actions (Recommended)

**When**: GitHub Actions automatically deploys on tag push  
**Status**: Monitor on Actions tab

```
GitHub → Actions → Deploy with Helm → (Find your run) → deploy-rc job
```

Expected output:
```
+ helm upgrade laa-data-access-api-rc .helm/data-access-api ...
Release "laa-data-access-api-rc" has been upgraded. Happy Helming!
```

### Manual Deployment (Emergency/Testing)

Only use if GitHub Actions fails:

```bash
# 1. Authenticate to cluster
export KUBE_CLUSTER="[cluster-name]"
export KUBE_NAMESPACE="laa-data-access-api-uat"

# Configure kubeconfig (using secrets from GitHub)
kubectl config set-cluster $KUBE_CLUSTER --certificate-authority=/path/to/cert
kubectl config set-credentials $(whoami) --token=$(cat /path/to/token)
kubectl config set-context default --cluster=$KUBE_CLUSTER --user=$(whoami) --namespace=$KUBE_NAMESPACE

# 2. Authenticate to ECR
aws configure
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin [ECR_REGISTRY]

# 3. Get image details
ECR_REGISTRY="[your-registry]"
ECR_REPOSITORY="[your-repo]"
IMAGE_TAG="[git-sha]"  # From the commit you're deploying

# 4. Deploy with Helm
helm upgrade laa-data-access-api-rc .helm/data-access-api \
  --namespace $KUBE_NAMESPACE \
  --set image.repository="${ECR_REGISTRY}/${ECR_REPOSITORY}" \
  --set image.tag="${IMAGE_TAG}" \
  --set spring.profile="unsecured" \
  --set aws.region="eu-west-2" \
  --values .helm/data-access-api/values/rc.yaml \
  --install \
  --wait \
  --timeout=10m

# 5. Verify
kubectl get deployment -n $KUBE_NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc
# Expected: READY 2/2 (or more if auto-scaled)
```

---

## Post-Deployment Verification

After deployment completes, verify RC is healthy:

### Check Kubernetes Resources

```bash
NAMESPACE="laa-data-access-api-uat"

# List all RC resources
kubectl get all -n $NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc

# Expected output:
# - Deployment: laa-data-access-api-rc, READY 2/2
# - Pods: 2 running (laa-data-access-api-rc-xxxxx)
# - Service: laa-data-access-api-rc, ClusterIP
# - Ingress: laa-data-access-api-rc, ingress-class=modsec-non-prod
```

### Test Connectivity

```bash
# Get pod name
POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc -o jsonpath='{.items[0].metadata.name}')

# Port-forward and test
kubectl port-forward -n $NAMESPACE $POD 8080:8080 &
sleep 2

# Test health endpoint
curl -i http://localhost:8080/health

# Expected: HTTP 200 OK
# Kill port-forward
jobs -p | xargs kill
```

### Verify PostgreSQL

```bash
# Check Bitnami PostgreSQL pod
kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=postgresql,app.kubernetes.io/instance=laa-data-access-api-rc

# Expected: 1 pod running (PostgreSQL)

# Test connection
POD=$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/name=postgresql,app.kubernetes.io/instance=laa-data-access-api-rc -o jsonpath='{.items[0].metadata.name}')

# Port-forward
kubectl port-forward -n $NAMESPACE $POD 5432:5432 &
sleep 2

# Test SQL connection
PGPASSWORD=$(kubectl get secret -n $NAMESPACE laa-data-access-api-rc-postgresql -o jsonpath='{.data.postgres-password}' | base64 -d) \
  psql -h localhost -U postgres -d postgres -c "SELECT 1"

# Expected: (1 row) with value "1"

# Kill port-forward
jobs -p | xargs kill
```

### Check Metrics

```bash
# Wait 2-3 minutes for metrics to populate

# Access Grafana
# URL: [Cloud Platform Grafana]
# Dashboard: "laa-data-access-api-rc-dashboard"
# Verify:
# - Metrics showing (not empty)
# - Request count increasing
# - No error spikes
# - CPU/memory reasonable
```

---

## Monitoring

### Continuous Monitoring During RC

Monitor for the first 24 hours after deployment:

**Every 1 hour:**
```bash
# Pod status
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Recent logs (any errors?)
kubectl logs -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc --tail=50

# Resource usage
kubectl top pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
```

**Every 4-6 hours:**
```bash
# Check Grafana dashboard for trends
# Look for:
# - Steadily increasing request count (good)
# - No error rate spike (bad)
# - CPU/memory stable (good)
# - Slow queries (monitor thresholds)
```

### Alert Thresholds

Set up or monitor these alerts:

| Alert | Condition | Action |
|-------|-----------|--------|
| **Pod Restart** | Pod restarted | Check logs, investigate cause |
| **High CPU** | >70% for 5+ min | Check request load, may auto-scale |
| **High Memory** | >80% for 5+ min | Assess if clients driving load or memory leak |
| **Error Rate** | >1% errors | Check logs, notify team |
| **Pod Not Ready** | Pod not ready for 5+ min | Describe pod, check events |

### Grafana Dashboard Details

**URL**: [Cloud Platform Grafana]  
**Dashboard**: `laa-data-access-api-rc-dashboard` (auto-created)  
**Key panels**:
- Request rate (requests/sec)
- Response time (p50, p95, p99)
- Error rate (errors/sec)
- CPU usage
- Memory usage
- Database connection pool
- Slow queries (queries >threshold)

---

## Troubleshooting

### Pod Not Starting

**Symptoms**: `kubectl get pods` shows `ImagePullBackOff`, `CrashLoopBackOff`, or `Pending`

**Debug steps**:

```bash
# 1. Describe pod for events
kubectl describe pod -n laa-data-access-api-uat <pod-name>

# Look for:
# - Image pull errors → Check ECR, image tag
# - Insufficient resources → Check cluster capacity
# - CrashLoopBackOff → Check app logs (below)

# 2. Check pod logs
kubectl logs -n laa-data-access-api-uat <pod-name> --tail=100

# Look for:
# - Spring Boot startup errors
# - Database connection issues
# - Configuration errors

# 3. If pod crashed, check previous logs
kubectl logs -n laa-data-access-api-uat <pod-name> --previous --tail=100

# 4. Check resource requests vs. cluster capacity
kubectl describe nodes | grep -A 10 "Allocated resources"
```

**Common fixes**:

| Error | Cause | Fix |
|-------|-------|-----|
| `ImagePullBackOff` | Image not in ECR | Check image tag in values, push to ECR |
| `CrashLoopBackOff` | App failing to start | Check logs, app configuration |
| `Pending` | No node with capacity | Drain nodes, add capacity, or reduce resources |
| `OOMKilled` | Out of memory | Increase memory request/limit |

### Pod Running but Unhealthy

**Symptoms**: Pod running but health checks failing, app unresponsive

```bash
# 1. Check readiness/liveness probes
kubectl get pod -n laa-data-access-api-uat <pod-name> -o yaml | grep -A 10 "livenessProbe\|readinessProbe"

# 2. Port-forward to pod and test manually
kubectl port-forward -n laa-data-access-api-uat <pod-name> 8080:8080 &
curl -i http://localhost:8080/health
curl -i http://localhost:8080/api/records  # Try actual endpoint
jobs -p | xargs kill

# 3. Check app logs for errors
kubectl logs -n laa-data-access-api-uat <pod-name> -f --tail=50

# Look for:
# - Database connection errors
# - Spring Boot startup errors
# - Configuration issues
```

### PostgreSQL Not Responding

**Symptoms**: App logs show "cannot connect to PostgreSQL", database query timeouts

```bash
# 1. Check PostgreSQL pod
kubectl get pod -n laa-data-access-api-uat -l app.kubernetes.io/name=postgresql,app.kubernetes.io/instance=laa-data-access-api-rc

# If not running, restart
kubectl delete pod -n laa-data-access-api-uat <postgres-pod>
# Helm will restart it

# 2. Port-forward to PostgreSQL
kubectl port-forward -n laa-data-access-api-uat <postgres-pod> 5432:5432 &
PGPASSWORD=$(kubectl get secret -n laa-data-access-api-uat laa-data-access-api-rc-postgresql -o jsonpath='{.data.postgres-password}' | base64 -d) \
  psql -h localhost -U postgres -c "SELECT 1"

# 3. Check PostgreSQL logs
kubectl logs -n laa-data-access-api-uat <postgres-pod> -f

# 4. Check disk space (Bitnami PostgreSQL can fail if PVC full)
kubectl get pvc -n laa-data-access-api-uat | grep postgresql
kubectl describe pvc -n laa-data-access-api-uat <pvc-name>
```

### Ingress Not Resolving

**Symptoms**: DNS lookup fails, 502 Bad Gateway, connection refused

```bash
# 1. Check ingress exists and has IP
kubectl get ingress -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Expected: INGRESSCLASS, HOSTS, ADDRESS (IP)

# 2. Check ingress details
kubectl describe ingress -n laa-data-access-api-uat <ingress-name>

# Look for:
# - Status showing ready
# - Backends pointing to service
# - Rules correct

# 3. Check DNS propagation (may take 5-10 minutes)
nslookup <rc-hostname>

# 4. If DNS resolves but connection fails, test service directly
kubectl get svc -n laa-data-access-api-uat laa-data-access-api-rc
kubectl port-forward -n laa-data-access-api-uat svc/laa-data-access-api-rc 8080:8080 &
curl -i http://localhost:8080/health
jobs -p | xargs kill
```

### High Memory Usage

**Symptoms**: Memory usage >80%, pod might OOMKill

```bash
# 1. Check current memory
kubectl top pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 2. Check JVM memory settings (Spring app)
kubectl exec -it -n laa-data-access-api-uat <pod-name> -- jps -l -m
# Look for: -Xmx setting (max heap)

# 3. Check for memory leaks in logs
kubectl logs -n laa-data-access-api-uat <pod-name> | grep -i "memory\|heap\|gc"

# 4. Options to fix:
# - Increase memory limit in values/rc.yaml, redeploy
# - Reduce request load (notify clients of high load)
# - Investigate for memory leak (check app code)
```

### High CPU Usage

**Symptoms**: CPU consistently >70%, slow responses

```bash
# 1. Check current CPU
kubectl top pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 2. Check if auto-scaling (HPA) is active
kubectl get hpa -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 3. Describe HPA to see scaling activity
kubectl describe hpa -n laa-data-access-api-uat <hpa-name>

# 4. Check current replica count
kubectl get deployment -n laa-data-access-api-uat laa-data-access-api-rc

# 5. If scaling not helping:
# - Check application logs for slow queries
# - Check database slow-query logs
# - Notify clients of high load
```

---

## Common Operations

### Scale RC Manually

```bash
# Scale to 3 replicas (higher than default 2)
kubectl scale deployment -n laa-data-access-api-uat laa-data-access-api-rc --replicas=3

# Monitor scaling
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Reset to original (let HPA manage)
helm get values laa-data-access-api-rc -n laa-data-access-api-uat | grep minReplicas
# Then redeploy: helm upgrade ... (HPA will reset to min)
```

### Update RC Values Without Redeploying

```bash
# Edit resource limits, replica count, etc.
helm upgrade laa-data-access-api-rc .helm/data-access-api \
  --namespace laa-data-access-api-uat \
  --set resources.limits.memory="3G" \
  --values .helm/data-access-api/values/rc.yaml \
  --wait
```

### Rollback RC to Previous Version

```bash
# Check release history
helm history laa-data-access-api-rc -n laa-data-access-api-uat

# Rollback to previous release
helm rollback laa-data-access-api-rc 1 -n laa-data-access-api-uat

# Or rollback to specific release number
helm rollback laa-data-access-api-rc 3 -n laa-data-access-api-uat
```

### Delete RC (Cleanup)

```bash
# Uninstall Helm release (removes all RC resources + PostgreSQL)
helm uninstall laa-data-access-api-rc -n laa-data-access-api-uat

# Verify deleted
kubectl get all -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
# Should return nothing
```

### Restart RC (Force Pod Restart)

```bash
# Delete pods (Deployment will recreate)
kubectl delete pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Monitor restart
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc -w
```

---

## Client Issue Response

### Issue: "RC endpoint returns 500 errors"

**Triage**:
```bash
# 1. Check if RC is running
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 2. Check pod logs
kubectl logs -n laa-data-access-api-uat <pod-name> --tail=100 | grep -i "error\|exception"

# 3. Check if specific endpoint or all endpoints
# (Ask client for error details, curl example)

# 4. Check database
kubectl logs -n laa-data-access-api-uat -l app.kubernetes.io/name=postgresql,app.kubernetes.io/instance=laa-data-access-api-rc
```

**Likely causes**:
- Database connection lost → Restart PostgreSQL pod
- Out of memory → Increase memory limit, redeploy
- Application exception → Check logs, may need code fix
- Configuration issue → Verify environment variables set correctly

### Issue: "RC endpoint very slow"

**Triage**:
```bash
# 1. Check resource usage
kubectl top pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 2. Check slow query logs
kubectl logs -n laa-data-access-api-uat -l app.kubernetes.io/name=postgresql,app.kubernetes.io/instance=laa-data-access-api-rc | grep "slow"

# 3. Check Grafana for response time graphs
# Dashboard: laa-data-access-api-rc-dashboard
# Panel: Response time (p95, p99)

# 4. Check client load
# Are multiple clients testing simultaneously?
```

**Likely causes**:
- Client sending high volume of requests → Throttle, auto-scale helps
- Slow query in database → Optimize query, may need index
- Resource contention → Increase replicas manually
- High memory usage causing GC → Increase memory limit

### Issue: "Cannot connect to RC endpoint"

**Triage**:
```bash
# 1. Verify ingress is set up
kubectl get ingress -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# 2. Check DNS
nslookup <rc-hostname>

# 3. Test service directly
kubectl port-forward -n laa-data-access-api-uat svc/laa-data-access-api-rc 8080:8080 &
curl -i http://localhost:8080/health
jobs -p | xargs kill
```

**Likely causes**:
- DNS not yet propagated (wait 5-10 minutes)
- Service not ready → Check pod status
- Ingress not provisioned → Check ingress resources
- Firewall rules → Contact network team

---

## Maintenance Tasks

### Weekly Checks

```bash
# 1. Verify RC still running
kubectl get deployment -n laa-data-access-api-uat laa-data-access-api-rc

# 2. Check for pod restarts
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc -o wide
# RESTARTS should be 0

# 3. Review error logs
kubectl logs -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc --since=168h | grep -i error | wc -l

# 4. Check Grafana dashboard
# Look for trends, anomalies
```

### Monthly Review

```bash
# 1. Summarize RC usage
# - How many clients tested?
# - How many issues reported?
# - Average response time?

# 2. Review Prometheus metrics
# - Request volume trends
# - Error rate trends
# - Performance trends

# 3. Assess resource allocation
# - Was CPU/memory adequate?
# - Did HPA scale appropriately?
# - Any wasted resources?

# 4. Document learnings
# - What went well?
# - What needs improvement?
# - Update runbook if needed
```

---

## Emergency Procedures

### RC Down (Complete Outage)

**Actions (in order)**:

1. **Notify clients immediately** (email + Slack)
   - "RC temporarily unavailable, investigating"
   - ETA for restoration

2. **Triage**:
   ```bash
   kubectl get all -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
   # See what's wrong (pod crashed, no pods running, etc.)
   ```

3. **Restart RC**:
   ```bash
   # Option A: Restart pods
   kubectl delete pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
   
   # Option B: Rollback if recent bad deployment
   helm rollback laa-data-access-api-rc -n laa-data-access-api-uat
   ```

4. **Verify recovery**:
   ```bash
   # Pods running?
   kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc
   
   # Endpoint responding?
   curl -i https://<rc-endpoint>/health
   ```

5. **Notify clients**: "RC restored, ready for testing"

6. **Root cause analysis**: What went wrong? Document for prevention.

### Data Loss (PostgreSQL)

**Prevention** (ephemeral DB, so data loss is expected on RC deletion)

**If unexpected data loss occurs**:

1. **Check backup**:
   ```bash
   # Bitnami PostgreSQL may have snapshot
   kubectl get pvc -n laa-data-access-api-uat
   ```

2. **Restore from backup** (if available):
   ```bash
   # Depends on backup system; contact Cloud Platform team
   ```

3. **Recreate test data**:
   - Use API to recreate needed test data
   - Or request test fixture from client

---

## On-Call Handover

When handing off to next on-call engineer:

**Checklist**:
- [ ] RC currently healthy? (All pods running, endpoint responding)
- [ ] Any known issues? (Explain to incoming engineer)
- [ ] Any pending client issues? (Hand off tickets)
- [ ] Any manual monitoring needed? (Explain cadence)
- [ ] Any scheduled operations? (E.g., RC cleanup date)

**Template message**:
```
RC Status: [OK / ISSUES]

Current Issues (if any):
- [Issue 1: Description, troubleshooting steps taken, status]

Pending Items:
- [Client issue X: What they reported, action taken]

Action Items for Next Shift:
- [Check X at Y time]
- [Follow up with client Z about issue]

Contact: [Escalation contact if major issue]
```

---

## Quick Links

- **Cluster Access**: [Cloud Platform docs]
- **Helm Docs**: https://helm.sh/docs/
- **Kubernetes Docs**: https://kubernetes.io/docs/
- **Grafana**: [URL]
- **Runbook Repo**: This file + linked docs
- **Escalation Contact**: [On-call manager]
- **Team Slack**: [Channel]

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 21 May 2026 | Platform Team | Initial operations runbook |

