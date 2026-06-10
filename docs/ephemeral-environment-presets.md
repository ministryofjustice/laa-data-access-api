# Ephemeral Environment Presets

This approach deploys multiple short-lived environments after each merge to `main`, where each environment has exactly one feature flag enabled (plus a baseline with none enabled).

## Why this approach

- Avoids hardcoding env combinations in workflow YAML.
- Keeps feature-flag and env configuration in one preset catalog.
- Lets one build artifact be tested across multiple runtime configurations.

## Preset catalog

Presets live in `.github/config/ephemeral-environment-presets.json`.

Each preset defines:

- `suffix`: appended to the main-run release name base. Use lowercase snake_case without the `feature` prefix.
- `extraEnv`: comma-separated `UPPER_CASE_KEY=value` pairs injected directly as env vars.

Feature toggles are declared explicitly in `extraEnv` only.

During release-name generation, underscores in the suffix are normalized to hyphens to satisfy Kubernetes/Helm naming rules.

The default catalog contains:

- `baseline` (all flags explicitly set to `false`)
- `FEATURE_ENABLE_DEV_TOKEN`
- `FEATURE_DISABLEJPAAUDITING`
- `FEATURE_DISABLE_SECURITY`
- `FEATURE_EXAMPLE_FEATURE_FLAG`

Each non-baseline preset enables exactly one flag and keeps the others disabled.

## Workflow usage

The `Deploy Feature Flagged Environment` workflow now runs automatically on `push` to `main`.

For a merge commit on `main`, releases are generated from the configured suffix values, for example:

- `main-<sha>-baseline`
- `main-<sha>-feature-enable-dev-token`
- `main-<sha>-feature-disablejpaauditing`
- `main-<sha>-feature-disable-security`
- `main-<sha>-feature-example-feature-flag`

Each release is deployed and smoke-tested independently in the matrix.

## Env merge order

For each deployed environment, values come directly from `.github/config/ephemeral-environment-presets.json`.

## Cleanup

Use your existing cleanup workflow/process to remove short-lived releases after verification.

Releases are merge-scoped by naming (`main-<sha>-...`), so ensure your cleanup process handles these short-lived main-run releases.
