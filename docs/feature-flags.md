# Prompt: Implement ConfigMaps Feature Flag POC for Existing Spring Boot App (Phase 1: Manual Validation)

Use this prompt with a coding assistant to implement the Phase 1 POC—manual validation of ConfigMaps-based flags before adding CI automation.

## What are ConfigMaps-based Feature Flags?

Feature flags stored in **Kubernetes ConfigMaps** where:

- Feature flag values are stored as key-value data in Kubernetes ConfigMaps.
- Changes can be delivered manually (via kubectl or Lens) or automated (via CI, in Phase 2).
- The Spring Boot application reads flag values from the ConfigMap as configuration properties.
- Pod restarts trigger the app to load updated flag values (no runtime refresh required in this POC).
- Flag-only changes **do not require** rebuilding or redeploying the application Docker image.

**Phase 1** validates the core concept with manual updates via Lens or kubectl. **Phase 2** (optional) automates updates via GitHub Actions CI workflow.

This is the simplest path to externalize flag management and decouple flag updates from application deployment.

## Prompt

You are implementing **Phase 1** of a feature flag POC: validate that **ConfigMaps-based flags work with manual updates** in an **existing Spring Boot application**.

### Context

- The app currently uses Spring properties/environment variables for feature flags.
- The app does **not** currently use Spring Cloud Kubernetes.
- The app does **not** currently have any runtime refresh process.
- For this POC, prioritize simplicity and low risk.

### POC Goal (Phase 1: Manual Validation)

Validate that feature flags work with Kubernetes ConfigMaps:

1. Flag values are sourced from a Kubernetes ConfigMap.
2. ConfigMap is updated manually via kubectl or Lens.
3. **No container image rebuild is required** for flag-only changes.
4. Pod restart (manual via Lens or kubectl) picks up new flag values immediately.
5. Behavior change is observable and reversible.

**Phase 2 (optional):** Automate ConfigMap updates via GitHub Actions CI workflow once manual flow is validated.

### Scope

In scope (Phase 1):

- Add/adjust configuration so flags can come from ConfigMap-backed values.
- Keep existing Spring-based flag usage pattern.
- Add deployment/config changes needed for ConfigMap-driven flags.
- Create initial ConfigMap manifest for POC namespace.
- Add a lightweight test controller with endpoints that expose current flag values (for easy verification).
- Validation via manual ConfigMap updates (kubectl or Lens) and manual pod restart.
- Rollback instructions.

Out of scope (Phase 1):

- GitHub Actions CI workflow (Phase 2).
- Spring Cloud Kubernetes runtime refresh.
- Multi-cluster promotion design.

Optional (Phase 2):

- Automate ConfigMap updates via GitHub Actions.

### Phase 1 Implementation Requirements

1. Keep changes minimal and POC-friendly.
2. Keep app defaults safe if ConfigMap keys are missing.
3. Update deployment to mount the ConfigMap and inject values into Spring Boot config.
4. Add a simple test controller (e.g., `/flags` endpoint) that returns current flag values as JSON for easy verification.
5. Ensure manual ConfigMap updates and pod restarts are straightforward (work well via Lens or kubectl).
6. Preserve existing app behavior when flags are unchanged.
7. Document RBAC or access requirements for manual ConfigMap updates and pod restart operations.

### (Future) Phase 2 Implementation Requirements

Once manual flow is validated, add:

1. Ensure namespace-scoped, least-privilege CI access to target namespaces.

### Phase 1 Deliverables

Provide:

1. Files changed and why (app config, deployment, ConfigMap binding, test controller).
2. ConfigMap manifest(s) for POC environment with initial flag values.
3. Test controller code (minimal Spring Boot @RestController with `/flags` endpoint to return flag values as JSON).
4. Manual validation checklist:
   - App runs with default ConfigMap values.
   - ConfigMap is successfully mounted/read by pod.
   - `/flags` endpoint shows current flag values.
   - Update ConfigMap value via kubectl or Lens.
   - Restart pod (manual via Lens or kubectl rollout restart).
   - `/flags` endpoint now shows the updated flag values.
   - No image rebuild occurred.
5. Rollback instructions (restore previous ConfigMap, restart pod).
6. (Optional) Sketch of Phase 2 GitHub Actions workflow for future automation.

### Phase 1 Acceptance Criteria

Manual validation is complete when all are true:

1. ConfigMap is created and mounted into deployment.
2. App reads ConfigMap values at startup (no image rebuild required).
3. Test controller `/flags` endpoint is accessible and returns current flag values.
4. Manual ConfigMap update via kubectl/Lens changes the stored value.
5. Manual pod restart picks up the new ConfigMap value.
6. Test controller `/flags` endpoint reflects the updated flag values after restart (easy confirmation).
7. Rollback via restoring previous ConfigMap + pod restart works cleanly.
8. Existing non-flag functionality remains unchanged during all updates and restarts.

### Phase 1 Execution Order (Manual Validation)

1. Identify existing flag properties and current binding.
2. Create ConfigMap manifest mapping those properties.
3. Update deployment to mount ConfigMap and inject values into app config.
4. Add test controller with `/flags` endpoint to expose current flag values.
5. Deploy to non-production namespace and verify app reads ConfigMap values.
6. Call `/flags` endpoint to confirm it returns current flag values.
7. Manually update a ConfigMap value via kubectl or Lens.
8. Manually restart pod via kubectl rollout restart or Lens.
9. Call `/flags` endpoint again to verify it reflects the new flag values (easy confirmation of success).
10. Document rollback process (restore ConfigMap, restart pod).
11. Note any issues or surprising behavior for Phase 2.

### Sample Commands for Phase 1 Manual Validation

Replace `<namespace>` with your POC namespace, `<configmap-name>` with the ConfigMap name, `<deployment-name>` with the deployment name, and `<flag-key>` with the flag property name.

**1. View current ConfigMap values:**
```bash
kubectl get configmap <configmap-name> -n <namespace> -o yaml
```

**2. Edit ConfigMap directly (opens editor):**
```bash
kubectl edit configmap <configmap-name> -n <namespace>
```

**3. Update a single ConfigMap key (patch):**
```bash
kubectl patch configmap <configmap-name> -n <namespace> -p '{"data":{"<flag-key>":"true"}}'
```

**4. Restart deployment to pick up ConfigMap changes:**
```bash
kubectl rollout restart deployment/<deployment-name> -n <namespace>
```

**5. Check rollout status:**
```bash
kubectl rollout status deployment/<deployment-name> -n <namespace>
```

**6. Verify test controller sees new flag values:**
```bash
kubectl port-forward -n <namespace> deployment/<deployment-name> 8080:8080
# In another terminal:
curl http://localhost:8080/flags
```

**7. View pod restart history:**
```bash
kubectl get pods -n <namespace> -o wide
```

**8. Rollback: restore previous ConfigMap version and restart:**
```bash
# Apply previous ConfigMap manifest
kubectl apply -f <previous-configmap-manifest>.yaml -n <namespace>
# Restart deployment
kubectl rollout restart deployment/<deployment-name> -n <namespace>
```

### Phase 2 (Future: CI Automation)

Once Phase 1 is validated:

1. Create/update GitHub Actions workflow to apply ConfigMap manifest from flag repo.
2. Add rollout restart step.
3. Add approval gates and protected environment rules.
4. Test end-to-end from flag repo commit to pod behavior change.

### Known Phase 1 Limitations (must be stated)

1. No live in-process refresh; manual pod restart required to pick up changes.
2. Potential short-lived pod version skew during rollout.
3. Manual updates only—no automated drift reconciliation or audit trail yet (Phase 2 adds CI automation).
4. Requires cluster admin or RBAC permissions to update ConfigMaps and restart pods (via kubectl or Lens).

### Output Format

Return:

1. Summary of approach for Phase 1 (manual validation).
2. Exact file changes (app config, deployment, ConfigMap, test controller).
3. Test controller code (minimal Spring Boot @RestController exposing `/flags` endpoint with current flag values).
4. ConfigMap manifest example.
5. Deployment manifest changes (ConfigMap mount, environment variable injection).
6. Manual validation steps (kubectl or Lens commands for ConfigMap update + pod restart + `/flags` endpoint verification).
7. Validation evidence checklist.
8. Rollback procedure.
9. (Optional) Sketch of Phase 2 GitHub Actions workflow for future automation.
10. Risks and next-step recommendations (brief).
