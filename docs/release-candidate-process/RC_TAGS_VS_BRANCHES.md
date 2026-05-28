# Release Candidate Tags vs. Branch-Based Approaches

**For**: Stakeholders, Team Members, and Decision-Makers  
**Purpose**: Understand why Release Candidate tags are superior to branch-based workflows  
**Last Updated**: 27 May 2026

---

## Executive Summary

The Release Candidate (RC) tagging approach provides a stable, predictable way for clients to test upcoming features **without slowing down development or creating merge conflicts**. By using tagged snapshots from the main development branch, the team avoids the complexity and risk of maintaining an intermediate "pre-staging" or release branch.

**Key Benefits:**
- ✅ Clients get stable, frozen integration points for testing
- ✅ Main branch stays fast-moving (no blocking, no branch drift)
- ✅ No cherry-picking, rebasing, or merge conflicts
- ✅ Clear versioning and release history
- ✅ Lightweight to operate and maintain

---

## The Problem We're Solving

**Current Situation:**
- We do not use GitFlow. We have feature branches that merge into `main`.
- `main` deploys automatically to our UAT environment.
- We have a manual gate to promote `main` (UAT) directly to Staging.
- Clients can only test features after they reach the Staging environment.
- By then, any breaking changes are already "live" for testing, which may break clients temporarily.
- No opportunity for compatibility validation before Staging impact.

**The Alternative Proposal:**
- Create a `pre-staging` branch cut from `main`.
- Deploy this `pre-staging` branch for clients to test.
- Fix issues on `pre-staging` and eventually deploy it to Staging.

**The Tension:**
- Development moves fast on main (changes daily, not guaranteed stable)
- Clients need predictable, stable snapshots to test against
- Simply giving clients UAT access doesn't work (main is too volatile)
- We need something **stable but not Staging**

---

## Approach 1: Release Candidate Tags (RECOMMENDED)

### How It Works

Release Candidate tags capture **a frozen snapshot of main** at a specific point in time, deployed to a dedicated RC environment.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│  Feature Branch ─── Merge ──→  Main Branch ─── Tag: v1.2.3-rc.1 ──┐   │
│  (Development)                 (Fast-Moving)                       │   │
│                                                                     │   │
│                                    ↓                               │   │
│  Feedback & Fixes  ← Client Testing ← RC Environment              │   │
│        ↑                           (Stable Snapshot)               │   │
│        └───────────── Merge to Main ────────────────┘             │   │
│                                                                     │   │
│                           Tag: v1.2.3                              │   │
│                                  ↓                                  │   │
│                           Staging ─── Approve ──→  Production      │   │
│                       (Stable Release)                              │   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**

| Aspect | Description |
|--------|-------------|
| **Source** | Snapshot taken from main branch |
| **Versioning** | Semantic version with `-rc.N` suffix (e.g., `v1.2.3-rc.1`) |
| **Deployment** | Dedicated, isolated RC environment |
| **Database** | Persistent PostgreSQL instance (preserves test data and validates migrations) |
| **Lifetime** | Active until next RC created (typically 1-2 weeks) |
| **Promotion** | When validated, promote to Staging as final release |

### Timeline: Feature to Staging

```
RELEASE CANDIDATE TIMELINE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Day 1    ┌─ Feature Complete
         ├─ Code merged to main
         └─ All tests passing, Feature ready

Day 2    ┌─ RC Created
         ├─ Tag pushed (v1.2.3-rc.1)
         └─ RC deployed, Environment live

Day 3-5  ┌─ Client Testing
         ├─ Clients validate integration
         └─ Issues reported, Team fixes bugs

Day 6    ┌─ Bug Fix RC
         └─ v1.2.3-rc.2 deployed

Day 7    ┌─ Validation Complete
         ├─ No more issues
         └─ Promotion Decision: Ready for Staging

Day 8    ┌─ Final Release Tag
         ├─ v1.2.3 created
         └─ Deploy to Staging, Clients notified

Day 14   ┌─ Production Release
         └─ Final deployment

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### What Happens at Each Stage

#### 1. **Feature Development (Any Branch)**
- Developers work on feature branches
- Each PR gets a temporary preview environment
- Testing is fast and isolated
- No impact on main or other teams

#### 2. **Merge to Main**
- Feature merged after approval
- Main branch accepts changes frequently
- Tests run, but main may be unstable
- This is expected and normal

#### 3. **Create RC Tag**
- When feature is ready for client testing
- Team decides: "This is stable enough for clients"
- Tag snapshot from main: `v1.2.3-rc.1`
- Frozen in time — doesn't change even if main keeps moving

#### 4. **Deploy RC**
- Automated deployment to RC environment
- Migrations run against persistent DB to validate existing data isn't broken
- Ingress configured and live
- Clients notified with endpoint URL

#### 5. **Client Testing**
- Clients test their integration
- Report issues (bugs, API misunderstandings)
- Team can prioritize fixes

#### 6. **Handle Issues**
- If bugs found: fix in main, create new RC tag (`v1.2.3-rc.2`)
- Clients re-test on new RC
- Repeat until confident

#### 7. **Promote to Staging**
- When RC passes validation
- Create final release tag: `v1.2.3`
- Automatically deploys to Staging
- Old RC environment can be cleaned up
- Clients move integration to Staging

---

### Database Options for RC

The RC approach supports two database configurations, depending on how many clients are testing and how much isolation is needed.

#### Option A: Shared Persistent Database (Default)

A single persistent PostgreSQL instance shared by all clients testing against the RC.

```
SHARED DATABASE (OPTION A)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Client A ──┐
             ├──→  RC Environment  ──→  Shared Persistent DB
  Client B ──┘                          (single instance)

  + Migrations validated against real accumulated data
  + Lower infrastructure cost (one DB)
  - Client A's test data is visible to Client B
  - One client's bad data or migration issue affects all clients
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Best for:** A single client testing at a time, or clients whose data does not need to be isolated from each other.

#### Option B: Per-Client Isolated Databases

Each client gets their own RC environment and dedicated database instance. All environments run the same code from the same tag — the database is the only thing that differs.

```
PER-CLIENT DATABASES (OPTION B)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Tag: v1.2.3-rc.1
         │
         ├──→  RC Environment (Client A)  ──→  DB (Client A only)
         │     laa-data-access-api-rc-apply
         │
         └──→  RC Environment (Client B)  ──→  DB (Client B only)
               laa-data-access-api-rc-decide

  Same code. Same tag. Isolated data.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Best for:** Multiple clients testing simultaneously where:
- Clients must not see each other's test data
- Clients need to seed different test fixtures
- A client's data setup would interfere with another client's assertions

#### Choosing Between Options

| Factor | Option A: Shared DB | Option B: Per-Client DB |
|--------|--------------------|-----------------------|
| **Infrastructure cost** | Lower (one DB) | Higher (one DB per client) |
| **Data isolation** | None between clients | Full isolation |
| **Test fixture control** | Shared, may conflict | Each client controls their own |
| **Migration validation** | Single accumulated dataset | Per-client, may diverge |
| **Operational overhead** | Low | Medium (more environments to manage) |
| **Best when** | One client at a time | Multiple clients simultaneously |

> **Note:** Both options run the **same code from the same RC tag**. Per-client databases do not require separate branches or separate releases — the code is identical. This is what makes per-client isolation tractable: it is a deployment configuration choice, not a code branching decision.

---

## Approach 2: "Pre-Staging" Branch Workflow (Not Recommended)

### How It Works

This approach proposes cutting a `pre-staging` branch from `main` and deploying it as the client testing environment. When a bug is found during client testing, there are two ways the team could handle it:

- **Variant A — Fix on the branch**: Fix the bug directly on `pre-staging`, then backport the fix to `main`
- **Variant B — Re-cut the branch**: Fix the bug on `main`, delete the old `pre-staging` branch, and cut a fresh one

Both variants share a root problem — they create a **parallel timeline** that splits away from `main`. Each variant then introduces its own additional problems on top.

```
THE SPLIT TIMELINE (BOTH VARIANTS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  main ── A ── B ── C ── D (New Feature) ── E ── F
               │
               └── pre-staging (client testing)

Main continues moving. Pre-staging is frozen (or re-cut).
Eventually these two must reconcile before Staging is released.
Regardless of variant, the team must answer:
"Does Staging get pre-staging, main, or a merge of both?"
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Problems Shared by Both Variants

#### Promotion Ambiguity: What Actually Goes to Staging?

```
PROMOTION COMPLEXITY (BOTH VARIANTS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Option 1: Deploy pre-staging to Staging
            → You miss everything that landed in main
              while client testing was happening

  Option 2: Merge main into pre-staging, then deploy
            → You introduce untested code right before release
            → Client sign-off is now invalid

  There is no clean option.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### No Immutable Version Record

With a branch, there is no auditable version number tied to what the client tested. The branch name (`pre-staging`) stays the same regardless of what commit it points to. When a client says "we tested and it passed", there is no artefact to record *which* version they approved.

---

### Variant A: Fixing Bugs Directly on the Branch

In this variant, bugs found during client testing are fixed directly on `pre-staging` and then cherry-picked back to `main`.

#### Backporting Burden

```
BACKPORTING BURDEN CYCLE (VARIANT A)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Client finds bug on pre-staging
                │
                ↓
      Dev fixes bug on pre-staging
                │
                ↓
      Fix must also go to main
      (or it regresses next release)
                │
                ↓
      Dev cherry-picks to main
                │
                ↓
      Merge conflicts likely
      (main has moved on)

Cost: Every fix requires two PRs, merge conflicts, regression risk
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

- Every client-found bug must be fixed **twice** — once on `pre-staging`, once on `main`.
- If the backport is forgotten, the bug reappears in the next release.
- Backporting frequently causes merge conflicts because `main` has moved ahead.

#### Database Schema Drift

If new migrations land on `main` (UAT) after the `pre-staging` branch was cut, the two environments diverge at the database level. When `pre-staging` is eventually promoted to Staging, it may be missing migrations that UAT has already applied — or vice versa. Flyway migrations are non-reversible, making this hard to recover from cleanly.

---

### Variant B: Re-Cutting the Branch After Each Fix

In this variant, bugs are fixed on `main` and the `pre-staging` branch is deleted and re-created from the new commit. This avoids backporting but introduces a different set of problems.

#### Zero Traceability

```
RE-CUT BRANCH: ZERO TRACEABILITY (VARIANT B)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Monday:  pre-staging → commit abc123  (client tests this)
  Tuesday: bug found, fix merged to main
  Tuesday: pre-staging deleted, re-cut → commit def456
  
  Client says: "We approved pre-staging on Monday"
  Team says:   "pre-staging is now def456"
  
  There is no record that abc123 ever existed as a named snapshot.
  The client's sign-off cannot be tied to a specific version.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### No Rollback

If the re-cut `pre-staging` introduces a regression, there is no way to redeploy yesterday's snapshot — the branch pointer was deleted. An RC tag (`v1.2.3-rc.1`) is immutable and can be redeployed at any time.

#### This Is Just RC Tags, Done Badly

If fixes go to `main` and the branch is deleted and re-pointed after each fix, the team is manually approximating exactly what RC tags do — but without version numbers, without immutable history, and without native CI/CD support. GitHub Actions, Helm, and GitHub Releases are all built around immutable tags. A floating branch name provides none of those guarantees and adds operational overhead instead of removing it.

---

## Side-by-Side Comparison

The pre-staging branch proposal has two variants. Both have distinct problems.

**Variant A** — fixes go directly onto the `pre-staging` branch:  
**Variant B** — fixes go to `main`, old branch deleted, new branch re-cut:

| Factor | **RC Tags (Main)** | **Variant A: Fix on Branch** | **Variant B: Re-Cut Branch** |
|--------|-------------------|------------------------------|------------------------------|
| **Main Branch** | Fast-moving, unblocked | Kept moving; branch drifts away | Kept moving |
| **Bug Fix Flow** | Fix on `main`, new tag | Fix on branch, then backport | Fix on `main`, delete + re-cut |
| **Backporting Effort** | Zero | High (every fix needs 2 PRs) | None |
| **Client Testing Reliability** | High (immutable tag) | Medium (backport may break main) | Low (every re-cut restarts testing) |
| **Version / Traceability** | Clear (`v1.2.3-rc.1`) | Ambiguous (branch name) | None (branch is deleted) |
| **Rollback to Previous Snapshot** | Instant (tag is immutable) | Not possible | Not possible (branch gone) |
| **Release History** | Searchable git tags | Buried in branch history | No history (branch deleted) |
| **CI/CD Pipeline Stability** | Native tag support | Requires branch tracking | Race conditions on re-cut |
| **Promotion Ambiguity** | Zero (deploy the tag) | High (branch vs. main?) | Medium (re-cut = same problem) |
| **Operational Load** | Low (automated) | High (manual backports) | Medium (manual re-cut process) |

---

## Why RC Tags Win

### 1. Single Source of Truth

```
SINGLE SOURCE OF TRUTH (RC TAGS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Feature Branch A
  Feature Branch B
  Feature Branch C
         │    │    │
         └────┼────┘
              ↓
         Main Branch
        (Single Truth)
              │
      ┌───────┼───────┐
      ↓               ↓
  Tag v1.2.3-rc.1  Tag v1.2.3
      ↓               ↓
  RC Environment   Staging

Benefit: All features converge to main, no branch maintenance
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

- All features converge to main
- All versions derived from main
- No branch maintenance overhead
- Easier to understand: "What's deployed? The tag."

### 2. Automatic Conflict Resolution

When using tags on main:
- Each commit to main goes through code review and tests
- Conflicts are resolved **once** (during the merge to main)
- No re-merging or re-resolving conflicts later
- Branches need only live as long as the PR review

### 3. True Feature Velocity

```
TRUE FEATURE VELOCITY (RC TAGS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Day 1              Day 2           Day 3-5         Day 7
  Feature Complete → RC Available → Testing → Staging Release
                    (24 hours)     (fast)     (immediate)

  Versus Pre-Staging Branch:
  Bug found → Fix on main → Delete old branch → Re-cut branch
  → Clients must re-test from scratch → Days/Weeks per cycle

RC Tags: Each RC is versioned; clients know exactly what they tested.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**With tags:**
- Feature merged → RC available in hours
- Bug found → fix on `main` → new RC tag → clients re-test only the fix
- Every test sign-off is tied to an immutable version number

**With a pre-staging branch:**
- Bug found → fix on `main` → re-cut branch → entire client test cycle restarts
- No version number exists to anchor what the client previously validated

### 4. Predictable Client Experience

```
PREDICTABLE CLIENT EXPERIENCE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Team                RC Env                      Client
    │                   │                           │
    ├─ Deploy v1.2.3-rc.1 ──→
    │                   ├─ Ready
    │                   ├─ RC available at URL X ──→
    │                   │                           │
    │                   │       Test for 3 days     │
    │                   │       No surprises        │
    │                   │       Code frozen         │
    │                   │                           │
    │                   │       Tests pass ✓ ←─────┤
    │                   │
    ├─ Promote to Staging
    │                                      Continue testing ←─┤

Benefit: Code frozen during testing, predictable, no surprises
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

- Client knows code won't change unexpectedly
- Testing window is predictable
- Feedback loops are clear

---

## When Would Branches Be Needed?

Branches are NOT the primary workflow, but they have narrow use cases:

| Scenario | Solution |
|----------|----------|
| **Urgent hotfix** | Feature branch → temporary environment → merge to main |
| **Experimental work** | Feature branch → preview environment → no client exposure |
| **Long-term research** | Feature branch → PR preview → no pressure to merge |
| **Very large feature** | Multiple PRs merged to main over time (no long branch) |

**Key Point:** Even in these cases, the code eventually merges to main and we use RC tags. Branches are temporary, not permanent.

---

## Adopting RC Tags

The team already uses the right foundation: feature branches → `main` → UAT → Staging gate. No workflow migration is needed. Adopting RC Tags means adding one step — tagging a commit on `main` before promoting to Staging.

### How to Start
1. **Identify the next client-facing release** — the next time a breaking change or significant new endpoint is ready
2. **Tag it as an RC** on `main` before the Staging gate is approved (`v1.2.3-rc.1`)
3. **Share the RC environment URL** with the relevant clients
4. **Collect feedback** — fix bugs on `main`, tag `rc.2` if needed
5. **Approve the Staging gate** once clients sign off

The existing manual gate to Staging stays in place; the RC is simply what clients test *before* that gate is approved.

---

## Decision Framework

**Use Release Candidate Tags if:**
- ✅ Development moves fast on main
- ✅ Multiple teams contribute to main
- ✅ Clients need predictable testing windows
- ✅ You want to avoid merge conflicts
- ✅ You value operational simplicity

**Consider branches only if:**
- You need complete isolation from main (experimental features)
- You're building a hotfix (temporary branch)
- You're stabilizing a massive release (time-bound, then merge)

---

## Key Takeaways

| Point | Implication |
|-------|-------------|
| **RC tags capture main at a point in time** | No branch maintenance, no rebase burden |
| **Frozen snapshots give clients stability** | Predictable testing, faster feedback loops |
| **Main stays fast-moving** | No blocking, no coordination overhead |
| **Single source of truth** | Easier release management, clearer audit trail |
| **Scales with multiple clients** | Multiple RC tags from same main, not multiple branches |
| **Lightweight operations** | Tag + deploy is minutes, not days |

---

## Next Steps

1. **Read [RC_WORKFLOW.md](./RC_WORKFLOW.md)** for step-by-step instructions on creating an RC
2. **Review [RC_OPERATIONS.md](./RC_OPERATIONS.md)** for operational procedures
3. **Share this document** with stakeholders to align on the approach
4. **Start with a pilot RC** to validate the workflow before full rollout

---

## Questions?

- **How do I create an RC?** → See [RC_WORKFLOW.md](./RC_WORKFLOW.md)
- **What if something goes wrong?** → See [RC_OPERATIONS.md](./RC_OPERATIONS.md)
- **Will clients understand this?** → See [CLIENT_INTEGRATION_GUIDE.md](./CLIENT_INTEGRATION_GUIDE.md)
- **How does this fit the full release process?** → See [release-process.md](./release-process.md)
