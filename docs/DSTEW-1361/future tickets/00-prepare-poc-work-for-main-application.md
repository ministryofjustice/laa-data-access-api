# Ticket 00: Prepare POC Work for Application to Main

**Status:** Ticket draft  
**Baseline:** `main`  
**Validated against:** `CreateApplication` POC branch work and partial `MakeDecision` POC work  
**Last reviewed:** 2026-04-16

Assumption: this ticket is only needed if the team intends to reuse or adapt work from the POC branch rather than re-implement everything directly from `main`.

## Jira Summary
Review, clean up, and reconcile the existing hexagonal POC work so it can be safely applied to `main` in small, reviewable slices.

## Problem Statement
The POC branch contains validated work for `CreateApplication` and partial work for `MakeDecision`, but that work was produced to prove the architecture rather than to land directly on `main`. Without an explicit cleanup step, the team risks carrying forward POC-only package choices, outdated assumptions, overly broad diffs, and documentation drift.

## Scope
- Review the POC branch changes for `CreateApplication` and `MakeDecision`.
- Identify which changes are reusable as-is, which need refinement, and which should be discarded.
- Split reusable work into logical delivery slices that match the future-ticket plan.
- Align naming, package choices, and status notes with the current master plan.
- Confirm the `main`-branch baseline for any code paths changed on the POC branch.
- Document the recommended apply strategy: cherry-pick, manual port, or selective reimplementation.
- Capture any blockers discovered while trying to bring POC work in line with `main`.

## Out of Scope
- Landing all hexagonal changes to `main` in this ticket.
- Introducing new architecture beyond what is already covered by the future-ticket set.
- Broad functional changes unrelated to reconciling POC work.

## Notes for Implementation
- Keep this ticket focused on readiness and decomposition, not on completing the whole migration.
- Prefer producing a clear mapping from POC changes to follow-on tickets over carrying forward a single large branch diff.
- Where POC package structure differs from the master plan, document whether to retain the interim structure temporarily or realign before merge.

## Acceptance Criteria
- AC1: The POC branch changes relevant to hexagonal migration are reviewed and categorized as reusable, revise-before-use, or discard.
- AC2: A documented plan exists for how validated POC work maps onto the future-ticket sequence for `main`.
- AC3: Any POC-only assumptions, naming choices, or package-layout deviations are identified and either accepted explicitly or queued for cleanup.
- AC4: The team has a clear recommendation for how to bring POC work onto `main` in small PRs.
- AC5: Follow-on tickets can be executed against `main` without ambiguity about whether they assume net-new implementation or reuse of POC work.

## Risks
- Cleanup work expands if the POC branch diverged materially from `main`.
- The team treats POC code as production-ready without enough decomposition or review.
- Decisions about interim vs end-state package layout are deferred too long and create churn in later tickets.

## Suggested Definition of Done
- A short reconciliation note or PR description exists showing how POC work will be applied to `main`.
- The next implementation ticket can start from a clean, agreed baseline.
