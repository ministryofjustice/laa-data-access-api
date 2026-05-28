# Problem Summary & Initial Analysis

## Problem Statement

**Core Issue**: ADS clients need the ability to integrate with new features during active development before those features are released to the Staging environment. Currently, this creates a coordination problem where:

1. **Timeline mismatch**: Clients want early access to validate compatibility before Staging release
2. **Stability tension**: Providing early access without breaking changes in Staging is challenging
3. **Readiness validation**: Clients need confidence that they can consume changes before official release

## Current State

**Existing Infrastructure** (already in place):
1. **Feature branches**: Each PR spins up a small ephemeral environment
2. **Main branch**: Deploys to UAT environment (used by ADS development team internally)
3. **Release**:  Releases deploy to Staging environment (where clients currently connect)

**The Gap**:
- Clients only have access to Staging
- Breaking changes reaching Staging create a problem: clients can't validate compatibility until changes are already live
- Clients want pre-Staging visibility to ensure they're ready to consume changes

**Key Constraint**:
- **Main branch is fast-moving**: Changes frequently, not guaranteed to match the next release
- This rules out simply giving clients UAT access (the API contract is not frozen and may change without notice)
- Clients need a **stable, predictable integration point** between UAT and Staging

## Context

- **ADS**: A REST-based API fronting a data store for consuming and providing application information
- **Timeline**: ASAP (high priority)
- **Team capacity**: Limited
- **Release cadence**: Feature branch → UAT (internal, fast-moving main) → Staging (client-facing) → Production
- **Client access**: Can be granted to external clients with appropriate controls

## Key Constraints

- **Limited team**: Implementation and ongoing maintenance must be lightweight
- **Dual stability requirement**: Protect Staging stability while enabling early integrations
- **Operational overhead**: Cannot add significant burden to deployment/support processes

## Initial Assessment of Proposed Approaches

Given that **main branch is fast-moving** and **clients need predictable integration points**, this eliminates the simple UAT-access solution. Focus shifts to creating stable, predictable environments:

### ✅ **Best Fit Approaches**
1. **Release Candidate Tags + Dedicated RC Environment** — RECOMMENDED
   - Create tagged RC releases deployed to a client-accessible RC environment
   - Clear versioning: v1.2.3-rc.1 (frozen snapshot from fast-moving main)
   - Clients test RCs before Staging release
   - Bridges the gap between fast-moving UAT and Staging
   - **Effort**: Medium (CI/CD tagging + RC environment + versioning process)
   - **Benefit**: Predictable, clean workflow, scales with multiple clients

2. **Per-Client Release Candidate Environments + Feature Flags** — FOR CLIENT-SPECIFIC ISOLATION
   - For cases where different clients need different features enabled (e.g., Apply needs schema v2, Decide stays on v1)
   - Tag with client suffix: `v1.2.3-rc.1-apply` or `v1.2.3-rc.1-decide`
   - Each client gets own RC environment + isolated database
   - Feature flags control what's enabled per client — same codebase on main, different behaviour
   - **Constraint**: Only works *after* code is merged to main (avoids long-running branch complexity)
   - **Effort**: Medium (per-client Helm values + feature flag support in application)
   - **Benefit**: True per-client isolation without branch divergence or cherry-pick complexity

3. **Temporary Feature Branches (On-Demand)** — LIGHTWEIGHT OPTION
   - For specific features/clients wanting early access
   - Deploy feature branch to isolated client environment
   - Time-limited, explicit ownership
   - Complements RC approach for urgent cases
   - **Effort**: Low (process definition + cleanup discipline)

### ⚠️ **Supplementary Options**
4. **API Mocking** — PARALLELIZATION TOOL
   - Allows clients to start integration work while features still in development
   - Works alongside RC approach
   - Reduces blocking on backend readiness
   - **Effort**: Medium-high (tooling + maintenance)

### ❌ **Not Ideal for This Situation**
- Ephemeral environments for clients (you have PR environments, but main is fast-moving so UAT isn't viable for client integration)
- Simple UAT access (API contract is not frozen, may change without notice)
- **Long-running per-client branches** — leads to cherry-pick/rebase complexity, merge conflicts, and divergence (see below)

---

## Per-Client Release Candidate Approach (Detailed)

For scenarios where different clients need different features enabled simultaneously (e.g., Apply wants to test schema v2, Decide needs to stay on v1), we can extend the Release Candidate approach with per-client environments and feature flags.

### How It Works

```
main (single source of truth)
  └── git tag v1.3.0-rc.1-apply
        └── GitHub Actions detects tag matching *-apply
              └── Helm deploy with release: laa-data-access-api-rc-apply
                    ├── values/rc-apply.yaml (feature flags enabled)
                    └── Own isolated Bitnami PostgreSQL database
```

- **Apply client** tests against `laa-data-access-api-rc-apply-uat.cloud-platform.service.justice.gov.uk`
- **Decide client** tests against `laa-data-access-api-rc-decide-uat.cloud-platform.service.justice.gov.uk`
- Both environments run **identical code** from the same commit on main — feature flags control behaviour

### Key Constraints

1. **Code must already be on main** — per-client environments only work *after* the feature is merged to main and deployed to UAT. This avoids long-running branch complexity.

2. **Feature flags are required** — to give different clients different behaviour from the same codebase. Without flags, you'd need different branches, reintroducing all the merge/rebase problems.

3. **Individual databases are non-negotiable** — schema migrations run on startup; if clients share a DB, one client's migration breaks the other.

### Why Not Long-Running Per-Client Branches?

| Problem | Detail |
|---|---|
| **Branch divergence** | Main continues to move; the client branch falls further behind |
| **Rebase burden** | Regular rebasing required, but each rebase invalidates the client's testing |
| **Cherry-pick complexity** | Creates duplicate commits, context loss, escalating merge conflicts |
| **CI false confidence** | Tests pass on branch but may fail once rebased onto current main |

Feature flags on main avoid all of this by keeping everything on trunk.

### Database Migrations (Expand and Contract)

Because both environments deploy the same codebase from main, any migration scripts will run in **both** client databases. Schema changes must follow the **Expand and Contract pattern**:

1. **Expand**: Add new columns/tables non-destructively. Both clients get the structure, but only one client's flag enables the API to use it.
2. **Contract**: Only after full promotion to Staging (all clients ready, flag removed) can you drop old columns.

### The Unavoidable Convergence Point

Per-client environments give clients **independent early access testing**, but not **independent production timelines**. Eventually all clients must converge:

```
RC (per-client flags) → Staging → Production
                                      ↑
                           ALL clients hit this
                           ALL flags permanently ON
```

The RC exists to ensure all clients are prepared before promotion — not to enable permanent per-client divergence.

### Documentation

Full technical details: `docs/release-candidate-process/per-client-integration.md`

---

## API Mocking: Detailed Assessment (AC4)

API mocking allows clients to begin integration work against a simulated version of the ADS API before the real endpoint or an RC environment is available. It is a **parallelisation tool** — it unblocks client development earlier in the cycle but is not a substitute for real environment testing.

### Use Cases

| Scenario | Value |
|---|---|
| New endpoint is in development; client wants to start coding their integration now | ✅ High — unblocks client development entirely |
| Breaking change to an existing endpoint; client wants to understand the new contract before an RC is ready | ✅ High — client can explore the shape and raise questions early |
| RC is available; client wants to validate real behaviour | ❌ None — use the RC endpoint directly |
| Performance or load testing | ❌ None — mocks cannot simulate real database or infrastructure behaviour |

### Tooling Options

This project has an OpenAPI specification (`data-access-api/open-api-specification.yml`), which in principle enables spec-driven mocking. However, the spec currently does not define `example` values for request/response schemas. Without examples, a spec-driven mock server returns only generic placeholder values (`"string"`, `0`, `true`) — not realistic enough for client integration work. This is a prerequisite gap that must be resolved before mocking can be offered to clients.

#### Option 1: Prism (Stoplight) — Recommended

Prism reads an OpenAPI spec and generates a mock server directly from it. No stub code to write or maintain.

```bash
# Install
npm install -g @stoplight/prism-cli

# Run mock server from the ADS OpenAPI spec
prism mock data-access-api/open-api-specification.yml
# → Mock server running at http://localhost:4010
```

Clients point their integration tests at `localhost:4010`. When the spec changes, the mock updates automatically on the next run — no manual stub maintenance.

**Advantages**: Zero stub maintenance once examples exist; validates request payloads against the schema; can run as a Docker container in client CI pipelines.

**Disadvantages**: Mock response quality depends entirely on the spec's `example` fields — which are currently absent. Without them, Prism returns placeholder values only. Even once examples are added, they can drift out of sync with the real API as the codebase evolves, silently giving clients a false picture of the actual contract.

#### Option 2: WireMock

Programmatic HTTP stub library. More control than Prism but requires stub definitions to be written and maintained manually alongside the codebase.

**When to prefer**: When clients need stateful responses (create then retrieve) or need to simulate specific error conditions that the spec alone cannot describe.

**Disadvantage**: Stub definitions drift from the real API unless actively maintained. Higher ongoing cost than Prism for this project.

#### Option 3: Postman Mock Servers

Postman can generate a hosted mock from a collection with a public or private URL.

**When to prefer**: Quick demos or proof-of-concept work where a hosted URL needs to be shared immediately without the client running anything locally.

**Disadvantages**: Requires a Postman subscription for private mocks; external SaaS dependency; not version-controlled alongside the codebase.

### Limitations Compared to Real Integration

| Capability | Mock (Prism) | RC Environment |
|---|---|---|
| Request/response shape validation | ✅ Yes | ✅ Yes |
| Real database operations | ❌ No | ✅ Yes |
| Schema migration correctness | ❌ No | ✅ Yes |
| Auth and security flows | ❌ No (pass-through) | ✅ Yes |
| Performance and load characteristics | ❌ No | ✅ Yes |
| Realistic error paths and edge cases | ⚠️ Limited to spec-defined examples | ✅ Yes |
| State persistence between calls | ❌ No | ✅ Yes |

### Maintenance and Synchronisation

The key risk with mocking is **spec drift** — the mock diverges from the real API and gives clients false confidence in an integration that will fail against the real endpoint. This risk exists for both approaches:

- **Prism (spec-driven)**: drift occurs when code changes are made without updating the spec `example` fields. This is easy to miss because the application compiles and tests pass regardless of whether the spec examples are current. There is currently no automated check that spec examples match real API behaviour.
- **WireMock (handcrafted stubs)**: drift occurs when stubs are not updated alongside code changes. The maintenance burden is higher but the stubs are deliberate and explicit, making drift more visible.

Mitigations:
- Treat spec `example` updates as part of the definition of done for any API change
- Include a switchover note in client communications when an RC supersedes a mock: *"RC v1.3.0-rc.1 is now available — please switch from the mock to the real RC endpoint"*
- Do not allow clients to continue testing against mocks once an RC exists
- Set expectations clearly: mocks validate the contract shape only; real behaviour can only be confirmed against the RC

### Developer Experience

**Internal team**: No new tooling or infrastructure to operate. However, the discipline required to make this viable is non-trivial: spec `example` fields must be added from scratch and then kept current with every API change. This is an ongoing authoring commitment, not a one-off task. Teams that do not currently maintain spec examples as part of their workflow often find that mocks quietly degrade in quality over time.

**Clients**: Low barrier — Prism is a single `npm install` and one command. Clients can also run it in Docker. The mock URL is `localhost:4010`, making it easy to swap in test configuration.

### Recommendation

API mocking is best positioned as an **opt-in unblocking mechanism** for clients during active backend development — before a feature is merged to main and before an RC exists. It shortens the feedback loop by allowing clients to start coding against the expected contract immediately.

However, **mocking is not currently viable for this project** in its present state. The spec lacks `example` values, meaning any mock generated today would return placeholder data with no resemblance to real responses. Before mocking can be offered to clients:

1. Spec examples must be added and reviewed for accuracy
2. A process must be established to keep them current (part of definition of done)
3. Clients must be clearly told that mocks validate contract shape only — not real behaviour, auth, data, or migrations

Given the ongoing drift risk and the discipline required, the team should weigh whether mocking adds sufficient value over simply creating an RC earlier. For most scenarios the RC is the more reliable option. Mocking is most justified when client development timelines mean they cannot wait for an RC at all.

### Implementation Steps

**Prerequisite (blocker)**: The following must be done before mocking can be offered to clients.

1. **Add `example` values to the OpenAPI spec** — Every request body and response schema in `data-access-api/open-api-specification.yml` needs realistic example values. This is a non-trivial one-off effort. Without it, Prism returns placeholder strings and numbers that are useless for client integration work.
2. **Establish a spec-update convention** — Agree that updating spec examples is part of the definition of done for any API change. Without this, the spec drifts silently and mocks become misleading.
3. **Add Prism setup instructions to the Client Integration Guide** — Once examples exist, a short section in `CLIENT_INTEGRATION_GUIDE.md` is sufficient: install Prism, point it at the spec URL, run tests.
4. **Publish the spec** — Share a link to the raw spec file on GitHub in client communications when a feature is in active development.
5. **No server-side infrastructure required** — clients run Prism locally or in their own CI. No ADS-side deployment needed.
6. **Define the switchover trigger** — when an RC is created, explicitly communicate to clients that the mock is now superseded. Include the RC URL in the same message.

---

## Comparative Analysis (AC5)

### Approach Comparison Matrix

| | **Ephemeral environments** | **Temporary branches** | **RC tags** | **API mocking** |
|---|---|---|---|---|
| **Scalability** | Low — one dedicated environment per client; grows linearly with client count | Low — branch divergence compounds with each additional client and each day on main | High — single RC tag serves all clients; per-client variant scales via pipeline matrix | High — no server infrastructure; clients run locally |
| **Maintenance effort** | Medium — Helm values, DB provisioning, and cleanup per client | High — regular rebase required to stay current; each rebase risks invalidating client testing | Low once established — tag, deploy, test, promote; flag cleanup is the only recurring discipline | Low if spec-driven (Prism); High if handcrafted stubs |
| **Client experience** | Good for isolated testing; poor for clients unfamiliar with the deployment lifecycle | Poor — testing surface is invalidated by each rebase; no stable, predictable snapshot | Good — frozen snapshot, predictable URL, clear version, documented onboarding | Limited — validates request/response shape only; cannot test real behaviour, auth, or data |
| **Speed of setup** | Slow — new Helm values, pipeline job, and DB provisioning required per client | Fast to create the branch; increasingly slow to maintain as main moves | Medium — CI/CD implemented (spike complete); a new RC requires only a tag push | Very fast — clients can start within minutes of receiving the spec URL |
| **Operational risk** | Medium — environment isolation limits blast radius; orphaned resources if cleanup discipline fails | High — merge conflicts, duplicate commit SHAs, CI false confidence, and divergence risk all grow over time | Low — RC is isolated from UAT and Staging; tag validation prevents off-main deployments | Very low — no server-side infrastructure; risk is confined to client-side false confidence from spec drift |

### Recommended Approach by Scenario

| Scenario | Recommended approach |
|---|---|
| Client wants to start integration work before the feature is merged to main | API mocking against the OpenAPI spec (Prism) |
| Feature merged to main; one or more clients need to validate against the real API before Staging | Standard RC tag (`v{version}-rc.{n}`) |
| Client A needs a feature enabled; Client B is not yet ready or unaffected | Per-client RC tag (`v{version}-rc.{n}-apply`) with feature flags |
| Urgent, time-critical feature; RC cadence adds too much overhead | Short-lived temporary branch (days only, explicit cleanup owner, treat as exceptional) |
| Multiple clients need simultaneous pre-Staging validation with data isolation | Per-client RC environments with isolated databases |

### Dependencies, Blockers, and Required Platform Changes

| Item | Impact | Status |
|---|---|---|
| RC environment uses Bitnami PostgreSQL rather than RDS | Reduces confidence in migration correctness; does not simulate real production DB lifecycle | Identified — RDS investigation is the agreed next step |
| OpenAPI spec `example` fields not defined (current gap) | Mocking is not currently viable — Prism returns placeholder values only. Even once added, examples can silently drift from real API behaviour. | Blocker for mocking option. Requires: (1) one-off example authoring effort, (2) spec-update discipline added to definition of done |
| Per-client RC environments not yet used with real clients | Pipeline and Helm values exist but are unvalidated in a live scenario | Proof of concept complete; pilot with one real client required |
| No formal process for communicating mock-to-RC switchover to clients | Clients may continue testing against stale mocks after an RC is available | Needs a short protocol added to the RC notification template |

---

## Revised Recommendations (Accounting for Fast-Moving Main)

**✅ CONFIRMED: Release Candidate + RC Environment Strategy**

**Create a Release Candidate workflow:**
1. Developers work on feature branches (spun-up ephemeral envs for testing)
2. Merge to main (which is allowed to change frequently)
3. When ready for client testing, tag an RC release (v1.2.3-rc.1)
4. Deploy RC tag to dedicated **RC environment** (client-accessible)
5. Clients test against frozen RC
6. Feedback loops, fixes merged back to main
7. When confident, create final release tag → Staging

**Why this works for you:**
- ✅ Clients get predictable, frozen integration points
- ✅ Main branch stays fast-moving (no breaking existing workflow)
- ✅ RC environment is isolated from fast-moving main/UAT
- ✅ Scales: multiple RCs can exist simultaneously for different clients
- ✅ Team can support it: RC env is duplication/reuse of Staging infrastructure

### Prerequisite: Feature Flag Discipline on Main

The RC model depends on one team practice that must be adopted alongside it: **any change that is not ready for the current release cycle must be behind a feature flag before merging to main.**

This is what makes the two core claims of the model simultaneously true: main can keep moving freely, and a new RC cut from main HEAD is always safe to give to clients.

The fix flow makes this concrete. When a client reports a bug against `rc.1`, the fix is merged to main and `rc.2` is cut from main HEAD. That new RC includes the fix and every other commit that landed on main since `rc.1`. If those other commits are unfinished features without flags, they are now exposed to clients via `rc.2`. If they are flag-gated (flag set to `false` in `rc.yaml`), they are inert — clients see only the fix.

**The rule**: if it is not ready for the next RC, it needs a flag. This is a prerequisite for the RC strategy, not an optional enhancement. Without it, the fix flow introduces unpredictable scope to clients and the safety guarantee of the model breaks down.

### Secondary: Temporary Feature Branches (Edge Cases)
- For urgent features: create feature branch → dedicated client env
- Limited lifetime with explicit cleanup
- Use when RC cadence is too slow

### Optional: API Mocking
- Parallel tracks: clients can mock + code while waiting for RC
- Reduces time-to-first-integration
- Add if integration is commonly blocked on backend work

---

## Added Benefits Over Current Approach

The current release process uses a manual approval gate in GitHub Actions — after UAT passes, a team member approves promotion to Staging. While functional, the Release Candidate approach provides several improvements:

### Better Release Documentation & Traceability

| Aspect | Current (Gate-Based) | Release Candidate Approach |
|---|---|---|
| **What's in Staging?** | A commit SHA that passed a gate | Explicit version tag (e.g., `v1.3.0`) |
| **What did clients test?** | Unknown — no client testing phase | RC tag (e.g., `v1.3.0-rc.1`) |
| **Release history** | Buried in GitHub Actions logs | Git tags — permanent, searchable |
| **Audit trail** | Manual gate approval timestamp | Tag timestamps + RC validation record |
| **Rollback reference** | Find the previous commit SHA | Previous version tag |

### Clearer Communication

- **Before**: "We deployed commit `a1b2c3d` to Staging"
- **After**: "We released `v1.3.0` to Staging — clients validated `v1.3.0-rc.1`"

### Answerable Questions

With the RC approach, these questions have clear answers:

- "What version is in Staging?" → `v1.3.0`
- "What version is in Production?" → `v1.2.0`
- "What are clients currently testing?" → `v1.3.0-rc.2`
- "What did Apply test before we released?" → `v1.3.0-rc.1-apply`
- "When was this version validated?" → Tag creation timestamp
- "What changed between releases?" → `git log v1.2.0..v1.3.0`

### Release Notes Per Version

Tags can have associated release notes (via GitHub Releases), providing:
- Changelog per RC and final release
- Breaking changes highlighted
- Migration guidance for clients
- Permanent documentation that doesn't get lost in Slack/email

### Simplified Rollback

- **Before**: Find the commit SHA of the previous working deployment
- **After**: Deploy the previous version tag (`v1.2.0`)

## Critical Questions for RC Implementation

1. **Versioning scheme**: ✅ **v.1.2.3-rc.1** (fixed)
2. **RC Lifetime**: ✅ **Active until next RC is created** (then previous RC is archived/removed)
3. **RC Infrastructure**: ✅ **Separate infrastructure** (independent from Staging to avoid resource contention)
4. **Client Communication**: ✅ **Direct notification + documentation page** (no API changelog discovery)
5. **RC Cadence**: How often should RCs be created? 
   - Per-feature (triggered manually)?
   - Weekly snapshot from main?
   - On-demand (when team says "ready")? ← **Recommended**
6. **Promotion path**: When an RC is validated, how does it become the Staging release?

## Spike Plan: RC Workflow Implementation

### Sprint 1: RC Infrastructure & Workflow Definition (1 week)

**Goal**: Define and prototype RC environment and workflow

**Key Decisions Made:**
- **RC Version format**: `v.1.2.3-rc.1` (e.g., v.1.2.3-rc.1, v.1.2.3-rc.2, etc.)
- **RC Lifetime**: Active until the next RC is created, then previous RC is archived/deprecated
- **RC Cadence**: On-demand when a stable feature is ready and will be released soon (i.e., final release timing is imminent)
- **RC Infrastructure**: Separate from Staging (independent resources, no contention)
- **Client Communication**: Direct notification + documentation page (no API-based discovery)

**Tasks:**
1. **RC Environment Setup**
   - Set up separate RC infrastructure (independent from Staging and UAT)
   - Verify CI/CD can deploy tagged RC releases independently
   - Configure access controls for external clients
   - Define environment resets between RCs (data migration strategy)

2. **RC Creation Trigger & Criteria**
   - Define what "stable feature ready" means (code review complete, internal testing passed, etc.)
   - Define what "release soon" means (timeline/window, e.g., "within 2 weeks")
   - Document decision-making process: who decides when to create an RC?
   - Create checklist for RC creation (feature freeze, release notes, client impact assessment)

3. **RC Notification & Documentation**
   - Create RC documentation page (endpoint URL, access instructions, known issues, current version)
   - Define client notification method (email? Slack? Other?)
   - Document release notes format for each RC (what changed, breaking changes, testing guidance)

4. **RC Promotion & Lifecycle**
   - Define promotion path: RC → validated → Staging release (when team decides RC is ready)
   - Automated archiving/cleanup of previous RC when new RC created
   - Determine if old RCs remain accessible for rollback or are fully removed

5. **Temporary Feature Branch Process** (backup for urgent cases)
   - Define when feature branch → temporary env is used
   - Ownership and cleanup responsibility
   - Deployment target (separate infra or reuse RC infra?)

6. **Per-Client RC Setup** (if per-client isolation is required)
   - Create per-client Helm values files (`values/rc-apply.yaml`, `values/rc-decide.yaml`)
   - Configure GitHub Actions to handle client-specific tags (`v1.2.3-rc.1-apply`, `v1.2.3-rc.1-decide`)
   - Each client environment gets isolated Bitnami PostgreSQL database
   - Define feature flag structure in Helm values and application code
   - Document tag naming convention and cleanup process

### Sprint 2: Pilot & Validation (1 week)

**Goal**: Test RC workflow with real clients, refine based on feedback

**Tasks:**
1. Select 1-2 pilot clients
2. Create first RC release (v.1.2.3-rc.1) on separate infrastructure
3. Send direct notification with RC details
4. Have clients test, collect feedback on:
   - Ease of access and notification
   - Environment stability and reliability
   - Documentation clarity
   - Integration feedback loops and response time
   - Time-to-test
5. Refine workflow and documentation based on learnings

### Sprint 3: Launch & Scale (Ongoing)

1. Finalize RC workflow, SLAs, and documentation
2. Train team on RC management, notification, and lifecycle
3. Prepare client communications (launch announcement, onboarding guide)
4. Monitor: track RC creation frequency, client adoption, issue detection rate, notification effectiveness

---

## Success Metrics

- **Lead time**: Time from code ready → client notified and testing on RC
- **Infrastructure**: RC environment uptime, stability, and isolation from Staging
- **Client satisfaction**: Feedback on notification timeliness, ease of access, documentation
- **Breaking change detection**: Issues found and fixed in RC before Staging release
- **RC cadence**: Frequency of RC releases (indicates feature velocity and predictability)