# Release Tag Validation: Quick Reference

**TL;DR**: Release tags must be created from commits on `main` branch.

---

## ✅ Correct Process

```bash
# 1. Switch to main and get latest
git checkout main
git pull origin main

# 2. Verify your commit is here
git log --oneline -1
# Output: abc1234 Merge pull request #123: Feature X

# 3. Create and push tag
git tag -a v1.2.3 -m "Release v1.2.3"
git push origin v1.2.3

# 4. Monitor workflow: GitHub Actions → Deploy with Helm
# Expected: validate-release-on-main job PASSES ✓
```

---

## ❌ What NOT to Do

```bash
# DON'T tag on a feature branch
git checkout feature/my-work
git tag v1.2.3
git push origin v1.2.3
# Result: Workflow FAILS at validation ✗

# DON'T tag with outdated local main
git checkout main
# (forgot to git pull)
git tag v1.2.3
# Result: Workflow FAILS at validation ✗

# DON'T tag detached HEAD
git checkout <commit-sha>
git tag v1.2.3
# Result: Workflow FAILS at validation ✗
```

---

## 🔧 If Validation Fails

```bash
# 1. Delete the tag
git tag -d v1.2.3
git push origin :v1.2.3

# 2. Get latest main
git checkout main
git pull origin main

# 3. Re-tag
git tag -a v1.2.3 -m "Release v1.2.3"
git push origin v1.2.3
```

---

## What Gets Validated?

| Trigger | Validated? | Why? |
|---------|-----------|------|
| Release tags: `v1.2.3` | ✅ YES | Must be on main |
| RC tags: `v1.2.3-rc.1` | ❌ NO | Different process |
| PR previews | ❌ NO | Different process |
| Main branch pushes | ❌ NO | Not applicable |

---

## Error Message

If validation fails, you'll see:
```
✗ ERROR: Tag commit is NOT on main branch!
Tag: refs/tags/v1.2.3
Commit: abc1234567890...

Release tags must be created on commits that are already merged to main.

To fix:
  1. Delete the tag: git tag -d v1.2.3
  2. Push deletion: git push origin :v1.2.3
  3. Merge your commit to main and run smoke tests
  4. Then re-tag from the commit on main
```

**Follow these steps** — they're printed by the validation job.

---

## Quick Check Before Pushing

```bash
# Before pushing the tag, verify it will pass:
TAG_COMMIT=$(git rev-list -n 1 refs/tags/v1.2.3)
git fetch origin  # Ensure origin/main is up to date
git merge-base --is-ancestor "$TAG_COMMIT" origin/main
# Output: 0 (exit code) = Will PASS ✓
# Output: 1 (exit code) = Will FAIL ✗
```

---

## Summary

1. **Merge to main first**
2. **Pull latest main locally**
3. **Tag from main**
4. **Push tag**
5. **Validation runs automatically**
6. **If it fails, follow printed steps**

---

## Need Help?

See detailed documentation:
- [RELEASE_TAG_VALIDATION.md](docs/release-candidate-process/RELEASE_TAG_VALIDATION.md) — Full technical guide
- [release-process.md](docs/release-candidate-process/release-process.md) — Release overview

