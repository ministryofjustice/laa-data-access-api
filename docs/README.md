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
