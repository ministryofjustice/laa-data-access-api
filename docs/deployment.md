# CI/CD - Deployment

## Continuous Integration (CI)
When a developer pushes code to GitHub:
- GitHub Actions triggers a workflow
- The workflow:
  - Builds the application
  - Runs unit tests and linting
  - Pushes the Docker image to a container registry

## Continuous Delivery/Deployment (CD)
Once the image is built and tested:
- GitHub Actions triggers a deployment workflow
- The workflow:
  - Uses Helm to upgrade or install the application in the Kubernetes cluster
  - Passes values to the Helm chart

# Helm

## Chart
1. Created the helm chart
    - `helm create .helm/data-access-api`
2. Created the helm values per namespace
    - `.helm/data-access-api/values/*.yaml`
    - and moved shared deployment values to `.helm/data-access-api/templates/deployment.yaml`

## Linting
To identify linting errors you can run:
- `helm lint .helm/data-access-api --values .helm/data-access-api/values/uat.yaml`

## Dry-run
To view the resulting yaml files that will be gnenerated by the helm templating engine but without actually installing/upgrading you can run:
- `helm template data-access-api .helm/data-access-api --values .helm/data-access-api/values/uat.yaml --dry-run --validate`

## Code Quality and Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) for automatic code formatting and [Checkstyle](https://checkstyle.sourceforge.io/) for code quality checks.

### Pre-commit Hooks Setup

To ensure code quality and consistent formatting, we recommend setting up pre-commit hooks that will automatically format your code before each commit.

#### Quick Setup

Run the setup script to configure Git hooks:

```bash
./scripts/setup-hooks.sh
```

#### Manual Setup

If you prefer to set up manually:

1. Configure Git to use the `.githooks` directory:
   ```bash
   git config core.hooksPath .githooks
   ```

2. Make the pre-commit hook executable:
   ```bash
   chmod +x .githooks/pre-commit
   ```

### What the Pre-commit Hook Does

The pre-commit hook will automatically:

1. **Format Java files** - Runs Spotless formatting on all staged `.java` files
2. **Apply code style** - Uses Google Java Format with import organization and unused import removal
3. **Run Checkstyle** - Validates code style compliance
4. **Stage formatted files** - Automatically adds the formatted files back to the commit

### Manual Spotless Commands

You can also run Spotless manually:

```bash
# Apply formatting to all files
./gradlew spotlessApply

# Check if files are properly formatted (without applying changes)
./gradlew spotlessCheck

# Apply formatting to specific files
./gradlew spotlessApply -PspotlessIdeHook="/path/to/file.java"
```

### Spotless Configuration

The Spotless configuration in `build.gradle` includes:

- **Google Java Format** - Standard Google code formatting
- **Import ordering** - Organizes imports in a consistent order
- **Unused import removal** - Removes unused import statements
- **CleanThat** - Additional code cleanup rules

### Troubleshooting

If you encounter issues with the pre-commit hook:

1. **Hook not running**: Ensure you've run `./scripts/setup-hooks.sh` or manually configured the hooks path
2. **Permission denied**: Make sure the hook is executable with `chmod +x .githooks/pre-commit`
3. **Formatting failures**: Run `./gradlew spotlessApply` manually to see detailed error messages

### Bypassing the Hook

In exceptional cases, you can bypass the pre-commit hook with:

```bash
git commit --no-verify -m "Your commit message"
```

**Note**: This should be used sparingly and only when absolutely necessary, as it bypasses code quality checks.