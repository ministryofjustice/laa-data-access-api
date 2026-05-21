# RC Workflow Guide

**For**: Team Members Creating and Managing Release Candidates  
**Last Updated**: 21 May 2026

---

## Overview

This guide explains how to create, manage, and promote Release Candidate (RC) environments. RCs are stable snapshots of the codebase deployed to a dedicated environment for client testing before the Staging release.

---

## Decision: Should We Create an RC?

Create an RC when **all of the following are true**:

1. **Feature is code-complete** and merged to main
2. **Internal testing is done** (unit tests, integration tests, smoke tests passing)
3. **Release is imminent** (timeline: within 1-2 weeks of planned Staging release)
4. **Feature has client impact** (API changes, breaking changes, new endpoints)
5. **Clients have requested early access** (or you expect they'll benefit from testing)

### Examples: When to Create an RC

✅ **DO create an RC when:**
- New API endpoint is ready; clients want to test integration
- Breaking change to existing endpoint; clients need to validate compatibility
- Database schema change; clients need to understand data implications
- Large feature affecting multiple clients; coordinated testing needed

❌ **DON'T create an RC when:**
- Bug fix (non-breaking, low risk)
- Internal refactoring (no client-facing changes)
- Feature is experimental (release is 4+ weeks away)
- Single client has blocked features (use temporary feature branch instead)

---

## Step 1: Preparation & Checklist

Before creating an RC, ensure:

### Code Readiness
- [ ] Feature branch merged to main
- [ ] All GitHub Actions checks passing (build, test, security scans)
- [ ] Code review completed and approved
- [ ] Release notes drafted (what changed, breaking changes)

### Feature Readiness
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Smoke tests passing (run locally: `./gradlew :data-access-service:infrastructureTest`)
- [ ] Manual QA testing completed (if needed)

### Client Communication
- [ ] Decision made: should clients test this?
- [ ] Clients identified who should test
- [ ] Expected timeline communicated ("RC available X date")

### Documentation
- [ ] API documentation updated (if changes made)
- [ ] Release notes written
- [ ] Migration guides (if database changes)

---

## Step 2: Create RC Tag

RCs use semantic versioning with `-rc.N` suffix:

```
v1.2.3-rc.1     ← First RC for version 1.2.3
v1.2.3-rc.2     ← Second RC (fixes applied, retesting)
v1.2.3          ← Final release
```

### Option A: Using GitHub CLI

```bash
# Create and push RC tag from main
git checkout main
git pull origin main

# Verify you're on the right commit
git log --oneline -1

# Create annotated tag (includes message)
git tag -a v1.2.3-rc.1 -m "Release candidate for v1.2.3"

# Push tag to GitHub
git push origin v1.2.3-rc.1
```

### Option B: Using GitHub Web UI

1. Go to **Releases** tab in GitHub
2. Click **Draft a new release**
3. Choose **Create a new tag** → enter `v1.2.3-rc.1`
4. Set target: `main`
5. Title: `v1.2.3-rc.1 (Release Candidate)`
6. Description: Paste release notes
7. Check **This is a pre-release**
8. Publish

---

## Step 3: Trigger RC Deployment

Once the tag is pushed, the deployment triggers automatically:

1. Go to **Actions** tab
2. Find **Deploy with Helm** workflow run for your tag (e.g. `v1.2.3-rc.1`)
3. Check that the `deploy-rc` job is running
4. Monitor logs for deployment progress

**Expected duration**: 5-10 minutes

> **If the workflow run is not visible:** Wait 30 seconds and refresh. If still missing, verify
> the tag format matches `v*-rc.*` exactly (e.g. `v1.2.3-rc.1`, not `1.2.3-rc.1`).
> The `push: tags: ['v*']` trigger only fires for tags starting with `v`.

---

## Step 4: Verify RC Deployment

Once deployment completes, verify RC is live:

### Check Kubernetes Deployment

```bash
# Set namespace variable
KUBE_NAMESPACE="laa-data-access-api-uat"

# Check pods are running
kubectl get pods -n $KUBE_NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc
# Expected: 2 Running pods (or more if auto-scaled)

# Check deployment status
kubectl get deployment -n $KUBE_NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc
# Expected: READY 2/2 (or more)

# View recent logs (troubleshoot if not running)
kubectl logs -n $KUBE_NAMESPACE -l app.kubernetes.io/instance=laa-data-access-api-rc -f --tail=50
```

### Test RC Endpoint

```bash
# External endpoint
curl -I https://<rc-release-name>-uat.cloud-platform.service.justice.gov.uk/health

# Expected: HTTP 200 OK (or 503 if starting up)
```

### Verify Metrics in Grafana

1. Open [Grafana dashboard](#) (Cloud Platform Grafana instance)
2. Find RC dashboard (named `laa-data-access-api-rc-dashboard`)
3. Check that metrics are being collected
4. Look for any error spikes

---

## Step 5: Notify Clients

Once RC is verified as live, notify clients:

### Email Template

```
Subject: Release Candidate v1.2.3-rc.1 Now Available for Testing

Hello [Client Team],

We've created a Release Candidate environment for you to test upcoming 
features before our Staging release.

🔗 RC Endpoint: https://<rc-release-name>-uat.cloud-platform.service.justice.gov.uk

What's New:
[Paste release notes here]

Getting Started:
1. Update your integration to point to the RC endpoint
2. Run your integration tests
3. Check for breaking changes in the release notes
4. Report any issues by replying to this email

Timeline:
- RC available: [date]
- Staging release planned: [date]
- Questions? Reply to this email or contact [support email]

Thanks,
[Your Team]
```

### Notification Channels
- Direct email to client technical contact
- Slack (if client has shared channel)
- API portal / developer hub (if you have one)

---

## Step 6: Support & Issue Resolution

### Client Issues During RC Testing

**If client reports a bug:**

1. **Acknowledge immediately** (within 2 hours)
   - "Thanks for reporting. We're investigating."
   
2. **Reproduce the issue**
   - Ask for: exact steps, error messages, payload/response
   - Replicate on RC environment
   
3. **Determine severity**
   - **Critical**: Blocks integration entirely (fix immediately)
   - **High**: Workaround exists but cumbersome (fix before Staging)
   - **Medium**: Feature works but needs polish (fix if time permits)
   - **Low**: Edge case, nice to have (document, fix in next version)
   
4. **Fix the bug**
   - Create PR with fix
   - Merge to main
   - Tag new RC: `v1.2.3-rc.2`
   - Deploy RC (see Step 3)
   - Notify client: "Fix deployed to RC v1.2.3-rc.2"
   
5. **Verify client's fix**
   - Client re-tests on new RC
   - Confirm issue resolved
   - Log issue for post-mortem

---

## Step 7: Promote RC to Staging Release

Once RC passes client testing and is ready for production:

### Promotion Decision

**Criteria to promote RC to Staging:**
- ✅ Client testing complete (at least 1 client tested successfully)
- ✅ No critical issues remaining
- ✅ All issues documented (known limitations)
- ✅ Release notes finalized
- ✅ Go/no-go decision made by Tech Lead

### Promotion Steps

1. **Tag RC as final release**
   ```bash
   # Create final release tag from main
   git tag -a v1.2.3 -m "Release v1.2.3"
   git push origin v1.2.3
   ```

2. **Trigger Staging deployment**
   - Pushing the final tag automatically triggers `deploy-staging` in the workflow
   - Staging requires manual approval via the GitHub environment protection gate
   - Go to **Actions** → find the workflow run for your tag → approve the `staging` environment deployment

3. **Verify Staging deployment**
   ```bash
   # Check Staging pods
   kubectl get deployment -n laa-data-access-api-staging laa-data-access-api
   # Expected: READY 2/2
   
   # Test Staging endpoint
   curl -I https://laa-data-access-api-staging.cloud-platform.service.justice.gov.uk/health
   # Expected: HTTP 200
   ```

4. **Notify clients**
   - Send email: "v1.2.3 released to Staging"
   - Include link to Staging endpoint
   - Thank them for testing RC

---

## Step 8: Archive or Clean Up RC

After Staging release, decide what to do with RC:

### Option A: Keep RC Around (Recommended)

Leave RC deployed for 1-2 weeks:
- Clients can verify Staging matches their RC testing
- Rollback point if Staging has issues
- Good reference for next RC creation

**Cleanup**: Delete after next RC created
```bash
helm uninstall laa-data-access-api-rc -n laa-data-access-api-uat
# This also removes PostgreSQL and ingresses
```

### Option B: Delete Immediately

```bash
helm uninstall laa-data-access-api-rc -n laa-data-access-api-uat
```

---

## Troubleshooting

### RC Pod Not Starting

```bash
# Check pod status
kubectl describe pod -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Check events (most recent at bottom)
kubectl get events -n laa-data-access-api-uat --sort-by='.lastTimestamp'

# View pod logs
kubectl logs -n laa-data-access-api-uat <pod-name> --previous  # If crashed
```

**Common causes:**
- Image not found in ECR (check image tag in values, ECR repository)
- Insufficient resources (check cluster capacity)
- ConfigMap/Secret missing (check Kubernetes secrets)

### RC Ingress Not Resolving

```bash
# Check ingress
kubectl get ingress -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Describe ingress (look for errors)
kubectl describe ingress -n laa-data-access-api-uat <ingress-name>

# Check DNS propagation (may take 5-10 minutes)
nslookup <rc-hostname>
```

### RC API Returns 503

```bash
# Check liveness probe
kubectl logs -n laa-data-access-api-uat <pod-name>

# Check if app is starting correctly
kubectl port-forward -n laa-data-access-api-uat <pod-name> 8080:8080
curl http://localhost:8080/health
```

### PostgreSQL Not Connecting

```bash
# Check Bitnami PostgreSQL pods
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/name=postgresql

# Check PostgreSQL logs
kubectl logs -n laa-data-access-api-uat <postgres-pod>

# Port forward to test locally
kubectl port-forward -n laa-data-access-api-uat <postgres-pod> 5432:5432
psql -h localhost -U postgres -d postgres -c "SELECT 1"
```

### Deploy Job Fails

Check GitHub Actions logs:
1. Go to **Actions** → **Deploy with Helm**
2. Find failed run
3. Expand failed step for error details

**Common causes:**
- Helm template errors (syntax)
- Image tag doesn't exist (ECR issue)
- Insufficient cluster resources
- Kubernetes credentials expired

---

## Monitoring RC Health

### Key Metrics to Watch

| Metric | Target | Alert If |
|--------|--------|----------|
| Pod CPU | <50% avg | >70% sustained |
| Pod Memory | <50% avg | >80% sustained |
| Response Time (p95) | <500ms | >1s |
| Error Rate | <0.1% | >1% |
| Pod Restarts | 0 | Any restarts |

### Access Grafana Dashboard

1. Open Cloud Platform Grafana
2. Search for `laa-data-access-api-rc-dashboard`
3. Monitor graphs for anomalies
4. Set up alerts if needed

### Regular Health Checks

**Before notifying clients:**
- Run smoke tests: `./gradlew :data-access-service:infrastructureTest`
- Test critical endpoints manually
- Check Grafana for errors/warnings

**Weekly (if RC running >1 week):**
- Review error logs
- Check resource utilization
- Verify PostgreSQL is healthy

---

## FAQ

**Q: Can we create multiple RCs simultaneously?**  
A: Yes, but recommended against. Each RC consumes resources; manage one at a time for clarity.

**Q: What if client testing takes longer than planned?**  
A: No problem. RC can stay active as long as needed. Document any delays; adjust Staging release date if required.

**Q: Can we rollback to a previous RC?**  
A: Yes. Helm keeps release history. To rollback:
```bash
helm rollback laa-data-access-api-rc -n laa-data-access-api-uat
```

**Q: What if we find a major bug after RC is created?**  
A: Create RC 2 with the fix (`v1.2.3-rc.2`). Follow same promotion process.

**Q: Who decides when to create an RC?**  
A: Tech Lead + Product Manager (together). Consider feature risk and client readiness.

**Q: Is RC data persistent between releases?**  
A: No. When RC is deleted, PostgreSQL is also deleted. Next RC starts with fresh database.

**Q: Can a client access both RC and Staging simultaneously?**  
A: Yes. Give them both URLs. They can compare behavior.

---

## Checklist Template

Copy and use this checklist when creating an RC:

```markdown
## RC v1.2.3-rc.1 Creation Checklist

### Preparation
- [ ] Feature merged to main
- [ ] All CI checks passing
- [ ] Release notes drafted
- [ ] Clients identified
- [ ] Timeline communicated

### Deployment
- [ ] RC tag created: v1.2.3-rc.1
- [ ] Tag pushed to GitHub
- [ ] Deploy workflow triggered
- [ ] Deployment successful
- [ ] Pods running (2+)
- [ ] Ingress resolving
- [ ] Health endpoint returning 200

### Verification
- [ ] Grafana dashboard created
- [ ] Smoke tests passing
- [ ] Manual API testing OK
- [ ] PostgreSQL accessible

### Client Notification
- [ ] Email sent with RC details
- [ ] Clients confirmed receipt
- [ ] Support contact provided
- [ ] Timeline clear

### Post-Creation
- [ ] Monitor for 24h
- [ ] Respond to issues
- [ ] Track testing progress
```

---

## Contact & Support

**Questions about RC workflow?**  
- Contact: [Tech Lead Name]
- Email: [email]
- Slack: [channel]

**Client issues during RC testing?**  
- Escalate to: [Product Manager]
- Support SLA: Acknowledge within 2h, resolve within 24h

