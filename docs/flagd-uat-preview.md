# flagd UAT Preview Runbook

This runbook describes how to validate `flagd` + OpenFeature on PR preview releases deployed into the shared `uat` namespace.

## Scope

- Namespace: `uat`
- Per-PR release naming: `<release>` (from existing preview release naming logic)
- Per-PR flagd resources:
  - ConfigMap: `<release>-data-access-api-flagd-flags`
  - Deployment: `<release>-data-access-api-flagd`
  - Service: `<release>-data-access-api-flagd`

## Expected app wiring

For preview releases, the app gets:

- `FLAGD_ENABLED=true`
- `FLAGD_HOST=<release>-data-access-api-flagd`
- `FLAGD_PORT=8013`

For `main` deployment in UAT, `FLAGD_ENABLED=false`.

## Verify resources after PR deploy

Replace `<release>` with the deployed preview release name.

```bash
kubectl -n uat get deployment "<release>-data-access-api-flagd"
kubectl -n uat get service "<release>-data-access-api-flagd"
kubectl -n uat get configmap "<release>-data-access-api-flagd-flags"
```

Check app env vars on the preview app pod:

```bash
kubectl -n uat get pods -l "app.kubernetes.io/instance=<release>,app.kubernetes.io/name=data-access-api"
kubectl -n uat describe pod <app-pod-name> | grep -E "FLAGD_ENABLED|FLAGD_HOST|FLAGD_PORT"
```

## Verify `/flags` output

If you can access the preview ingress URL, call it directly. If not, port-forward.

```bash
kubectl -n uat port-forward deployment/<release>-data-access-api 8080:8080
curl http://localhost:8080/flags
```

Expected default preview response:

```json
{"pocEnabled":true,"pocVariant":"control","pocVariantTwo":"treatment"}
```

## Manual flag change in UAT

Edit the per-release ConfigMap directly:

```bash
kubectl -n uat edit configmap "<release>-data-access-api-flagd-flags"
```

Update JSON in `flags.flagd.json`, for example:

- `poc-enabled.defaultVariant: "off"`
- `poc-variant-two.defaultVariant: "control"`

Then check again:

```bash
curl http://localhost:8080/flags
```

## If changes do not appear

1. Confirm the ConfigMap contains valid JSON.
2. Check `flagd` logs:

```bash
kubectl -n uat logs deployment/<release>-data-access-api-flagd --tail=200
```

3. Restart `flagd` deployment (usually enough):

```bash
kubectl -n uat rollout restart deployment/<release>-data-access-api-flagd
kubectl -n uat rollout status deployment/<release>-data-access-api-flagd
```

4. Re-check `/flags`.

## CI smoke assertion

PR preview deployments run a smoke assertion in workflow:

- file: `.github/workflows/build-test-deploy.yml`
- step: `Verify flagd-backed flags on preview releases`
- assertion: `/flags` must return expected preview values.

