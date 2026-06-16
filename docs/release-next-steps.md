# Release Process Next Steps (Proposed)

## Status

In progress. Workstream 2 items 1 and 2 are complete. Remaining workstreams are pending approval and implementation.

## Target Behavior

1. `main` merges deploy to UAT only. **[Implemented]**
2. RC tag creation (`v-X.Y.Z-rc.N`) triggers:
   - RC matrix deployment **[Implemented]**
   - staging deployment _(not yet implemented — staging still triggered by final tag)_
   - staging smoke tests _(not yet implemented)_
3. Final tag creation (`vX.Y.Z`) triggers:
   - staging deployment **[Current behaviour]**
   - staging smoke tests **[Current behaviour]**
   - production deployment **[Implemented]**
   - production smoke tests **[Implemented]**
4. Staging accepts only commits promoted with RC tags. _(not yet implemented)_

## Workstream 1: Policy and Governance

1. Approve tag naming conventions.
2. Approve who can create RC and final tags.
3. Decide if final release re-runs staging or goes direct to production.
4. Confirm signed tags policy.

## Workstream 2: Workflow Trigger Split

1. ~~Remove automatic RC matrix from `main` workflow.~~ **Done** — `deploy-main-uat.yml` now deploys to UAT only.
2. ~~Add RC-tag-triggered workflow for matrix.~~ **Done** — `deploy-release-candidate.yml` triggers on `v-*.*.*-rc.*` tags and runs RC matrix deployment.
3. Add staging deployment to the RC-tag workflow and remove it from the final-tag workflow. _(pending)_
4. Restrict production deployment trigger to final tags only. _(pending)_

## Workstream 3: Release Safety Gates

1. Keep main ancestry validation for all release tags.
2. Add guard requiring final tag commit to have prior RC tag evidence.
3. Keep deployment by immutable digest.
4. Keep blocking smoke tests between promotion steps.

## Workstream 4: Operational Hardening

1. Add or verify environment approvals.
2. Ensure branch/tag protections are active.
3. Ensure release managers have least privilege permissions.
4. Add alerting for release/tag/deploy events.

## Workstream 5: Documentation and Adoption

1. Finalize and publish release runbook.
2. Add examples for RC and final release creation via GitHub UI.
3. Align on-call checklist with workflow names.
4. Update team onboarding docs with release ownership model.

## Validation Plan

1. Dry-run RC release:
- create test RC tag on approved commit
- verify matrix + staging behavior
2. Dry-run final release:
- create test final tag on validated RC commit
- verify production behavior
3. Negative tests:
- tag not on main should fail
- final tag without RC evidence should fail (once gate is added)

## Completion Criteria

1. Trigger behavior matches approved target behavior.
2. Required checks and approvals are enforced.
3. Runbook is used in one real release and refined.
4. Post-release retrospective completed.
