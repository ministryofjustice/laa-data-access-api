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
3. **Release**: Tagged releases deploy to Staging environment (where clients currently connect)

**The Gap**:
- Clients only have access to Staging
- Breaking changes reaching Staging create a problem: clients can't validate compatibility until changes are already live
- Clients want pre-Staging visibility to ensure they're ready to consume changes

**Key Constraint**:
- **Main branch is unstable**: Changes often, not guaranteed to match the next release
- This rules out simply giving clients UAT access (it would be unpredictable)
- Clients need a **stable, predictable integration point** between UAT and Staging

## Context

- **ADS**: A REST-based API fronting a data store for consuming and providing application information
- **Timeline**: ASAP (high priority)
- **Team capacity**: Limited
- **Release cadence**: Feature branch → UAT (internal, unstable main) → Staging (client-facing) → Production
- **Client access**: Can be granted to external clients with appropriate controls

## Key Constraints

- **Limited team**: Implementation and ongoing maintenance must be lightweight
- **Dual stability requirement**: Protect Staging stability while enabling early integrations
- **Operational overhead**: Cannot add significant burden to deployment/support processes

## Initial Assessment of Proposed Approaches

Given that **main branch is unstable** and **clients need predictable integration points**, this eliminates the simple UAT-access solution. Focus shifts to creating stable, predictable environments:

### ✅ **Best Fit Approaches**
1. **Release Candidate Tags + Dedicated RC Environment** — RECOMMENDED
   - Create tagged RC releases deployed to a client-accessible RC environment
   - Clear versioning: v1.2.3-rc.1 (stable snapshot from unstable main)
   - Clients test RCs before Staging release
   - Bridges the gap between unstable UAT and Staging
   - **Effort**: Medium (CI/CD tagging + RC environment + versioning process)
   - **Benefit**: Predictable, clean workflow, scales with multiple clients

2. **Temporary Feature Branches (On-Demand)** — LIGHTWEIGHT OPTION
   - For specific features/clients wanting early access
   - Deploy feature branch to isolated client environment
   - Time-limited, explicit ownership
   - Complements RC approach for urgent cases
   - **Effort**: Low (process definition + cleanup discipline)

### ⚠️ **Supplementary Options**
3. **API Mocking** — PARALLELIZATION TOOL
   - Allows clients to start integration work while features still in development
   - Works alongside RC approach
   - Reduces blocking on backend readiness
   - **Effort**: Medium-high (tooling + maintenance)

### ❌ **Not Ideal for This Situation**
- Ephemeral environments for clients (you have PR environments, but main is unstable so UAT isn't viable)
- Simple UAT access (main branch churn makes it unreliable)

## Revised Recommendations (Accounting for Unstable Main)

**✅ CONFIRMED: Release Candidate + RC Environment Strategy**

**Create a Release Candidate workflow:**
1. Developers work on feature branches (spun-up ephemeral envs for testing)
2. Merge to main (which is allowed to be unstable)
3. When ready for client testing, tag an RC release (v1.2.3-rc.1)
4. Deploy RC tag to dedicated **RC environment** (client-accessible)
5. Clients test against stable RC
6. Feedback loops, fixes merged back to main
7. When confident, create final release tag → Staging

**Why this works for you:**
- ✅ Clients get predictable, stable integration points
- ✅ Main branch stays unstable (no breaking existing workflow)
- ✅ RC environment is isolated from chaotic main/UAT
- ✅ Scales: multiple RCs can exist simultaneously for different clients
- ✅ Team can support it: RC env is duplication/reuse of Staging infrastructure

### Secondary: Temporary Feature Branches (Edge Cases)
- For urgent features: create feature branch → dedicated client env
- Limited lifetime with explicit cleanup
- Use when RC cadence is too slow

### Optional: API Mocking
- Parallel tracks: clients can mock + code while waiting for RC
- Reduces time-to-first-integration
- Add if integration is commonly blocked on backend work

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