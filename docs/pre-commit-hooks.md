# Pre-commit Hooks for Code Quality and Security

## Code Quality and Formatting

This project uses [Ministry of Justice DevSecOps Hooks](https://github.com/ministryofjustice/devsecops-hooks) for security scanning.

### Pre-commit Hooks Setup

Automated pre-commit hooks run on each commit to help maintain code quality and security in this project.

#### Setup

**Note:** Some pre-commit hooks require Docker. Please ensure Docker is installed and running before running the setup script or making commits.

Run the setup script to configure Git hooks using Prek, which manages and runs pre-commit hooks as defined in .pre-commit-config.yaml:
```bash
./scripts/setup-git-hooks.sh
```

### What the Pre-commit Hooks Do

The pre-commit hooks will automatically perform:

1.**Security Scanning** - Run the MoJ security scanner to detect secrets and vulnerabilities.

### Configuration

The pre-commit configuration (`.pre-commit-config.yaml`) includes:

- **MoJ DevSecOps Hooks** - For security scanning.

For more information, see:
- [Pre-commit documentation](https://pre-commit.com/)
- [MoJ DevSecOps Hooks](https://github.com/ministryofjustice/devsecops-hooks)
