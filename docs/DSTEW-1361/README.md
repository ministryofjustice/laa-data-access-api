# Process Note

This folder has been reorganized to clarify the hexagonal architecture migration plan.

## Status

- **Main branch baseline:** No hexagonal architecture changes are merged.
- **POC branch status:** `CreateApplication` and parts of `MakeDecision` have been prototyped to validate the approach.
- **How to read this folder:** Some documents describe the `main` baseline, some capture findings from the POC branch, and some describe the intended target state. Prefer the master plan for the current agreed direction.

- **`master-plan/`**: This directory now contains the single, consolidated design document (`00-master-plan.md`) that outlines the motivation, target architecture, and phased strategy. This is the recommended starting point for understanding the migration.

- **`future-tickets/`**: This directory contains pre-written markdown drafts for each step of the migration. These are intended to be copied into Jira to create work items, but should be checked against the current branch baseline and any validated POC work before use.

- **`archive/`**: This directory contains the original, exploratory design documents. These are preserved for historical context but should not be considered the current plan or a live implementation guide. Some files reflect POC-only thinking or contain superseded paths and instructions.
