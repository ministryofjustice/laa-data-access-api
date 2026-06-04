# Technical Documentation

These files are used to build the technical documentation using the
[GOV.UK Tech Docs Template](https://github.com/alphagov/tech-docs-template).

The published documentation can be found [here](https://ministryofjustice.github.io/laa-data-access-api/).

## How to modify the documentation

The docs are built from the markdown files in `/docs/source/documentation`. To modify the published
documentation, edit those files. Once your changes have been merged into the main branch they will be published automatically.

## How to build the documentation locally

The makefile contains the commands to spin up a Docker container with the documentation:

```bash
cd docs
make preview
```

This will start a local server at `http://localhost:4567`.

## How is the documentation published

The `.github/workflows/publish-documentation.yml` workflow is used to publish the documentation using the
[MOJ Tech Docs GitHub Pages Publisher](https://github.com/ministryofjustice/tech-docs-github-pages-publisher) action.

The workflow triggers on merge to `main` when files in the `docs/` directory are changed.

## How to configure your GitHub settings

1. [Enable GitHub Pages](https://docs.github.com/en/pages/quickstart) for the repo
2. Under **Build and deployment**, select the **Source** to be **GitHub Actions**

---

## Additional Documentation

### CI/CD & Deployment
- **[deployment.md](./deployment.md)** — CI/CD overview and Helm deployment guide
- **[workflow-refactor.md](./workflow-refactor.md)** — Documentation of the June 2026 GitHub Actions refactor (commit `315e1b41`)
- **[github-actions-critical-evaluation.md](./github-actions-critical-evaluation.md)** — Critical evaluation of workflows (30 issues identified)
- **[github-actions-remediation-checklist.md](./github-actions-remediation-checklist.md)** — Action items checklist for fixing identified issues
- **[github-actions-quick-reference.md](./github-actions-quick-reference.md)** — Quick reference for workflow architecture and common tasks

### Infrastructure & Operations
- **[infrastructure-smoke-tests.md](./infrastructure-smoke-tests.md)** — Smoke testing guide
- **[monitoring.md](./monitoring.md)** — Monitoring and observability
- **[network-policies.md](./network-policies.md)** — Kubernetes network policies
- **[secure-document-storage.md](./secure-document-storage.md)** — Document storage architecture

### Development Process
- **[conventional_commits.md](./conventional_commits.md)** — Commit message conventions
- **[pre-commit-hooks.md](./pre-commit-hooks.md)** — Git hooks setup

