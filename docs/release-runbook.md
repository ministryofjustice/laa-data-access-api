# Release Runbook (Proposed)

## Status

This runbook describes the proposed operating model for release-candidate tags and final release tags.

It is intended for review and sign-off before workflow changes are fully aligned.

## Objective

Promote releases with explicit intent using GitHub Releases UI and immutable artifacts:

1. Merge to `main` deploys to UAT for early validation.
2. Creating a release-candidate tag promotes to RC matrix and staging.
3. Creating a final release tag promotes to production.

## Roles

1. Release Manager
- Creates RC/final releases in GitHub UI.
- Verifies release notes and release scope.

2. Engineer on Duty
- Monitors workflow runs and investigates failures.
- Confirms smoke test and deployment health.

3. Approver
- Approves protected environment deployments where required.

## Tag Conventions

1. RC tags
- Format: `vX.Y.Z-rc.N`
- Example: `v1.12.0-rc.1`
- Create as GitHub pre-release.

2. Final tags
- Format: `vX.Y.Z`
- Example: `v1.12.0`
- Create as full GitHub release.

## Pre-Release Checklist (RC)

1. Target commit is merged to `main`.
2. UAT deployment and smoke tests passed for target commit.
3. Any known risks are documented.
4. Release notes draft prepared.
5. No active release freeze.

## RC Release Procedure (GitHub UI)

1. Open GitHub Releases and select Draft a new release.
2. Select target commit on `main`.
3. Create tag `vX.Y.Z-rc.N`.
4. Mark as pre-release.
5. Publish release notes.
6. Verify expected workflows run and succeed:
- RC matrix deployment path.
- Staging deployment path.
- Staging smoke tests.
7. Capture evidence links (workflow runs, smoke report, digest).

## RC Exit Criteria

1. RC matrix completed successfully.
2. Staging deployment completed successfully.
3. Staging smoke tests passed.
4. Go/no-go decision recorded.

## Final Release Procedure (GitHub UI)

1. Draft final release from approved commit.
2. Create/publish `vX.Y.Z` tag.
3. Verify production deployment path starts.
4. Verify production smoke tests pass.
5. Publish release completion note.

## Rollback Procedure

1. Identify last known-good release tag.
2. Re-deploy known-good immutable artifact (tag/digest policy-compliant).
3. Re-run production smoke tests.
4. Publish incident/change summary.

## Mandatory Controls

1. Tag commit must be reachable from `main`.
2. Deploy by immutable image digest for tagged environments.
3. Staging smoke tests are blocking when staging is part of flow.
4. Environment approvals enabled for production.
5. Tag creation restricted to release managers.
6. Signed tags preferred.

## Evidence to Record Per Release

1. GitHub Release URL.
2. Tag name.
3. Commit SHA.
4. Image digest.
5. Workflow URLs.
6. Smoke test report links.
7. Approval record.
