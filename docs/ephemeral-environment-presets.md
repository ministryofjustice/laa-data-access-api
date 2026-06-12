# Ephemeral Environment Presets

This approach deploys multiple short-lived RC feature environments from the main deployment pipeline, where each environment maps to one preset entry (plus a baseline).

## Why this approach

- Avoids hardcoding env combinations in workflow YAML.
- Keeps feature-flag and env configuration in one preset catalog.
- Lets one build artifact be tested across multiple runtime configurations.

## Preset catalog

Presets live in `.github/config/ephemeral-environment-presets.json`.

Each preset defines:

- `suffix`: used to derive the Helm release name override. Prefer lowercase kebab-case.
- `extraEnv`: comma-separated `UPPER_CASE_KEY=value` pairs injected directly as env vars.

Feature toggles are declared explicitly in `extraEnv` only.

During matrix resolution, suffix values are sanitized for Kubernetes/Helm compatibility (lowercased, invalid chars replaced, and length-capped).

The current catalog contains:

- `baseline` (flags match production defaults)
- `FEATURE_DISABLEJPAAUDITING`
- `FEATURE_EXAMPLE_FEATURE_FLAG`

Each non-baseline preset should enable only the flag under test.

## Workflow usage

Preset resolution now runs inside `.github/workflows/build-test-deploy.yml` in the `base-resolve-presets` job.

On `push` to `main`, the flow is:

1. Build/test and image publish jobs complete.
2. UAT deploy and `uat-smoke-test` complete.
3. `base-resolve-presets` reads `.github/config/ephemeral-environment-presets.json` and emits a matrix.
4. `deploy-matrix-rc-feature` deploys one RC feature environment per matrix row.

This matrix deployment reuses the Docker image already pushed earlier in the same workflow (no additional image build step).

Example release-name overrides generated from the current catalog:

- `baseline`
- `disable-jpa-audit`
- `example-feature-flag`

## Env merge order

For each deployed environment, values come directly from `.github/config/ephemeral-environment-presets.json`.

## Cleanup

Use your existing cleanup workflow/process to remove short-lived releases after verification.

Releases are derived from preset suffixes, so ensure your cleanup process handles these short-lived RC feature releases.
