# Release Tag Validation

**Technical Safety Gate for Production Deployments**  
**Last Updated**: 22 May 2026

---

## Overview

This document explains the automated validation that ensures **release tags are only created on commits that have been merged to `main` and passed CI/CD gates**. This prevents accidental deployments of unvalidated code to Staging and Production.

---

## The Problem

### Issue: Release Tags Can Bypass CI/CD Gates

**Current risk (before this fix):**

1. Developer creates a feature on a branch
2. Developer tags the branch with a release tag (e.g., `v1.2.3`)
3. GitHub Actions builds and pushes the Docker image
4. The tag **never went through `main`**, so it never ran:
   - ❌ Smoke tests on main environment
   - ❌ Collaboration review gates
   - ❌ Team validation
5. Staging deployment is offered for approval
6. Reviewer might not realize the code never went through main
7. ❌ **Unvalidated code deployed to Staging/Production**

**Why this is dangerous:**

From [release-process.md](release-process.md):
> "The implicit expectation is that the tag is cut **after** the commit has already been merged to `main` and passed UAT smoke tests at that point. **If a tag is pushed on a commit that has not gone through `main`, there is no automated UAT gate protecting staging.**"

This relied on **process discipline** — documentation saying you *should* do it, not technical enforcement preventing you from doing it wrong.

### Example Scenario

```bash
# Feature branch (never merged to main)
git checkout -b feature/risky-change
git commit -m "Dangerous refactoring"

# Developer mistakenly tags it directly
git tag v1.2.3
git push origin v1.2.3

# ⚠️ PROBLEM: This code never went through main!
# But workflow still builds and offers it for staging approval
```

---

## The Solution: Automated Release Validation

A new workflow job `validate-release-on-main` runs **before staging deployment**, ensuring:

✅ The commit is reachable from `main` (i.e., it's been merged)  
✅ Prevents unvalidated code from reaching Staging  
✅ Fails fast with clear error messages  

### How It Works

When a release tag is pushed (e.g., `v1.2.3`):

```
1. Build & vulnerability scans run (same as before)
   ↓
2. NEW: validate-release-on-main checks if tag is on main
   ├─ If YES: ✓ Passes
   │          Staging deployment can proceed
   │
   └─ If NO: ✗ Fails
            Git command: git merge-base --is-ancestor <tag-commit> origin/main
            Workflow fails with helpful error message
            ❌ Staging deployment BLOCKED
```

### Workflow Changes

**File**: `.github/workflows/build-test-deploy.yml`

**New job**:
```yaml
validate-release-on-main:
  if: startsWith(github.ref, 'refs/tags/v') && !contains(github.ref, '-rc.')
  runs-on: ubuntu-latest
  permissions:
    contents: read
  
  steps:
    - name: Checkout (full history)
      uses: actions/checkout@... 
      with:
        fetch-depth: 0  # Critical: need full git history

    - name: Verify tag is on main branch
      run: |
        TAG_COMMIT=$(git rev-list -n 1 ${{ github.ref }})
        if git merge-base --is-ancestor "$TAG_COMMIT" origin/main; then
          echo "✓ Tag is on main branch - safe to deploy"
          exit 0
        else
          echo "✗ ERROR: Tag commit is NOT on main branch!"
          # [Helpful error message with remediation steps]
          exit 1
        fi
```

**Updated `deploy-staging`**:
```yaml
deploy-staging:
  # ...existing conditions...
  needs:
    - build-push-docker
    - vulnerability-scan-app
    - vulnerability-scan-docker
    - validate-release-on-main  # ← NEW: Must pass before staging
  # ...rest of job...
```

---

## What Triggers This Check

✅ **Checked**: Release tags matching `v*` without `-rc.` (e.g., `v1.2.3`)  
❌ **Not checked**: RC tags matching `v*-rc.*` (e.g., `v1.2.3-rc.1`)  
❌ **Not checked**: Pull requests, pushes to main, workflow_dispatch triggers

---

## Error Messages & Remediation

### Scenario 1: Tag on Unmerged Branch

```bash
# Developer tries to tag a feature branch
git checkout feature/my-work
git tag v1.2.3
git push origin v1.2.3
```

**Error in GitHub Actions**:
```
✗ ERROR: Tag commit is NOT on main branch!
Tag: refs/tags/v1.2.3
Commit: abc1234567890...

Release tags must be created on commits that are already merged to main.
This ensures the release has gone through the standard CI/CD gates.

To fix:
  1. Delete the tag: git tag -d v1.2.3
  2. Push deletion: git push origin :v1.2.3
  3. Merge your commit to main and run smoke tests
  4. Then re-tag from the commit on main
```

**How to fix**:
```bash
# 1. Delete the tag locally
git tag -d v1.2.3

# 2. Push the deletion (remove from remote)
git push origin :v1.2.3

# 3. Switch to main and pull latest
git checkout main
git pull origin main

# 4. Find your commit and verify it's on main
git log --oneline | grep "my work"

# 5. Re-tag from main (after merge, after smoke tests pass)
git tag -a v1.2.3 -m "Release v1.2.3"
git push origin v1.2.3
```

### Scenario 2: Tag on Outdated Main

```bash
# Old local main branch (before pulling latest)
git checkout main
# ⚠️ Forgot to git pull
git tag v1.2.3  # Based on old commit
git push origin v1.2.3
```

**Error**: Same as Scenario 1 (commit not reachable from `origin/main`)

**How to fix**:
```bash
git tag -d v1.2.3
git push origin :v1.2.3

git pull origin main  # ← Get latest main
git tag v1.2.3
git push origin v1.2.3
```

### Scenario 3: Tag Correctly on Main

```bash
# Correct process
git checkout main
git pull origin main

# Verify your commit is on main
git log --oneline -1
# Output: abc1234 Merge pull request #123: Feature X

# Create tag
git tag -a v1.2.3 -m "Release v1.2.3"
git push origin v1.2.3
```

**Validation passes**:
```
✓ Tag is on main branch - safe to deploy to staging
```

**Staging deployment proceeds** → Reviewer approval gate → Production

---

## Git Command Explanation

### `git merge-base --is-ancestor`

This command checks if one commit is an ancestor of another:

```bash
git merge-base --is-ancestor <commit> <branch>
```

- `<commit>`: The commit to check (extracted from the tag)
- `<branch>`: The reference branch (origin/main)
- **Exit 0** (success): Commit is an ancestor (on the branch)
- **Exit 1** (failure): Commit is NOT an ancestor (off the branch)

**Why this is reliable**:
- Works even if the branch has new commits (commit just needs to be reachable)
- Works with fast-forward merges and merge commits
- Works with rebased commits (as long as they're on main)
- Remote-agnostic (`origin/main` is the canonical source)

---

## Safety Considerations

### What This Prevents

✅ Tagging feature branches by mistake  
✅ Tagging unreviewed code  
✅ Tagging code that failed smoke tests  
✅ Deploying to Staging without going through main  

### What This Does NOT Prevent

❌ Tagging bad code that **is on main**  
❌ Bad code reviews that approved dangerous PRs  
❌ Data loss from bad migrations  
❌ Performance issues introduced to main  

**In other words**: This job validates **process compliance** (code went through main), not **code quality**. It's a gate, not a substitute for careful review.

---

## Reviewer Checklist

When approving a Staging deployment (`v*` tag without `-rc.`):

- [ ] Tag validation (`validate-release-on-main`) passed ✓
- [ ] Tag is on main (GitHub Actions confirmed)
- [ ] Release notes reviewed
- [ ] Breaking changes documented
- [ ] Clients notified (if applicable)
- [ ] Previous RC tested (if applicable)
- [ ] Rollback plan in place

---

## Troubleshooting

### "Tag is NOT on main branch" But I'm Sure I Merged It

**Possible causes**:

1. **Local main is out of sync**:
   ```bash
   git fetch origin  # Update remote tracking branches
   git log origin/main --oneline -1
   ```

2. **Tag is on a different branch**:
   ```bash
   git log $(git rev-list -n 1 refs/tags/v1.2.3) --oneline
   # Shows the commit; check if it's on your branch
   ```

3. **Merge didn't push**:
   ```bash
   git push origin main  # Ensure main is up to date on remote
   ```

### Validation Job Doesn't Run

**Check the `if` condition**:
- Tag must start with `v`: ✓ `v1.2.3`, ✗ `1.2.3`
- Tag must NOT contain `-rc.`: ✓ `v1.2.3`, ✗ `v1.2.3-rc.1`

```bash
# Wrong tag format (won't trigger validation)
git tag 1.2.3        # Missing 'v'
git tag v1.2.3-rc.1  # Contains '-rc.' (uses different job)

# Correct tag format
git tag v1.2.3       # Triggers validate-release-on-main
```

---

## FAQ

**Q: Can I use `git tag -d` to delete a tag locally without pushing the deletion?**  
A: Yes, but it won't remove it from GitHub. Someone else can still see and re-push it. Always push the deletion: `git push origin :v1.2.3`

**Q: What if I need to re-tag the same version (e.g., a typo)?**  
A: Delete the old tag, fix the commit, then create the new tag. Avoid re-using version numbers.

**Q: Can the validation be bypassed?**  
A: Only by:
1. Merging the commit to main first (defeats the purpose of bypassing)
2. Disabling the workflow (requires `admin` access; security event logged)

**Q: What if the commit is on a different main branch (e.g., `release/1.x`)?**  
A: This validation only checks `origin/main`. For other release branches, the team should have a separate process or modify this validation.

**Q: Does this work with force-pushes?**  
A: Yes, because the validation checks `origin/main`, which is the authoritative source even after force-pushes.

---

## Implementation Details

### Files Changed

1. **`.github/workflows/build-test-deploy.yml`**
   - Added `validate-release-on-main` job
   - Added `validate-release-on-main` to `deploy-staging` needs

### Execution Order

```
On release tag (v1.2.3) push:
├─ build-test (existing)
├─ vulnerability-scan-app (existing)
├─ vulnerability-scan-docker (existing)
├─ build-push-docker (existing)
├─ validate-release-on-main (NEW) ← Must succeed before staging
│  ├─ Checkout (with fetch-depth: 0)
│  └─ Check: is tag commit on origin/main?
│     ├─ If YES: exit 0 (pass)
│     └─ If NO: exit 1 (fail, block staging)
│
├─ deploy-staging (blocked until validate passes)
│  └─ Requires: staging environment approval
│
└─ deploy-production (after staging succeeds)
   └─ Requires: production environment approval
```

### Performance Impact

- **Runtime**: ~10 seconds (git checkout + one git command)
- **Resource cost**: Negligible (runs on ubuntu-latest)
- **Network**: One fetch to get full git history (already done at checkout)

---

## Related Documentation

- [release-process.md](release-process.md) — Overall release process
- [RC_WORKFLOW.md](RC_WORKFLOW.md) — How to create and manage RCs
- [RC_OPERATIONS.md](RC_OPERATIONS.md) — Operational procedures

---

## Questions or Issues?

If the validation fails unexpectedly or you believe there's a false negative:

1. Check the error message in the GitHub Actions logs
2. Verify your local/remote git state with `git fetch origin && git log origin/main`
3. Contact the team with:
   - Tag name
   - Expected commit SHA
   - Actual commit SHA shown in error
   - Screenshot of git log

