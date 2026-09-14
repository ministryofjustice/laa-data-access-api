# Prior Authority lifecycle status design

## Context

`PriorAuthorityResponse.status` was originally carrying decision-like values. The API is moving to a dedicated decision field, so `status` should now represent request lifecycle state.

## Goal

Make `status` lifecycle-only with exactly two values:

- `DRAFT`
- `SUBMITTED`

Apply this consistently to the command side, projection/read side, API response mapping, and tests.

## Scope

In scope:

- `data-access-service-axon` prior-authority decider and projection behavior
- Prior-authority status enum and status-dependent hydration logic
- Unit and integration tests asserting status values
- OpenAPI schema description/enumeration for `PriorAuthorityResponse.status`

Out of scope:

- Introducing the new dedicated decision field
- Any behavior changes outside prior-authority draft/submit lifecycle semantics

## Design

### 1. Status semantics

`PriorAuthorityStatus` becomes lifecycle-oriented with enum values:

- `DRAFT`
- `SUBMITTED`

No other values remain.

### 2. Event and projection flow

- On `PriorAuthorityDraftStartedEvent`, persist current-state row with `status = DRAFT`.
- On `PriorAuthoritySubmittedEvent`, persist current-state row with `status = SUBMITTED`.
- Keep `status` as a string column in `prior_authority_current_state`, but restore non-nullability:
  - remove `V10__allow_null_status_in_prior_authority_current_state.sql`

### 3. Hydration behavior

`PriorAuthorityProjection.hydrate(...)` resolves content by lifecycle status:

- `DRAFT` -> hydrate from `PriorAuthorityDraftStore` via `PriorAuthorityResult.fromDraft(...)`
- `SUBMITTED` -> hydrate from `PriorAuthorityDataStore` using projected `dataVersion`

Null-status draft detection is removed, since drafts now carry explicit lifecycle state.

### 4. API contract update

OpenAPI `PriorAuthorityResponse.status` is tightened to:

- `type: string`
- `enum: [DRAFT, SUBMITTED]`
- description updated to lifecycle wording

No response shape change; field remains `status`.

### 5. Test updates

Update prior-authority tests to assert lifecycle values:

- Draft status assertions become `DRAFT` (was `null`)
- Submitted status assertions become `SUBMITTED` (was `PENDING`)
- Decider/evolve/projection/mapper/integration expectations updated accordingly

## Error handling

No new exception paths are introduced. Existing not-found/validation behavior remains unchanged.

## Verification approach

Run the existing repository verification command:

- `./gradlew preflightCheck`

## Risks and mitigations

- **Risk:** Existing consumers may still interpret status as decision.
  - **Mitigation:** OpenAPI enum and description make lifecycle semantics explicit.
- **Risk:** Draft hydration regressions if status mapping is inconsistent.
  - **Mitigation:** Unit and integration assertions cover both draft and submitted retrieval.
