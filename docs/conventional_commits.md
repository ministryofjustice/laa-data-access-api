# Release Notes and Commit Management

We rely on **Conventional Commits** to standardize our commit history. This system is essential because it allows the **release-please** GitHub Action to automatically manage version bumping and generate accurate **Release Notes** and the `CHANGELOG.md`.

## 🤝 How to Write Commit Messages (Squash Merge)

When merging a Pull Request (PR) using **Squash and Merge**, the final squashed commit message **must** follow this structure: `<type>(<scope>): <description>`.

**The final squashed message is the only one used by the release automation.**

### Commit Types for Release Notes

The `<type>` prefix determines how the change is categorised in the Release Notes and whether a new release is required.

| Type | Significance in Release Notes                      | Version Bump |
| :--- |:---------------------------------------------------| :--- |
| **`feat`** | New Features                                       | **MINOR** (e.g., v1.2.0 → v1.3.0) |
| **`fix`** | Bug Fixes                                          | **PATCH** (e.g., v1.2.0 → v1.2.1) |
| **`perf`** | Performance Improvements                           | PATCH |
| **`docs`** | Documentation Updates                              | *(No Bump)* |
| **`chore`** | Routine maintenance (e.g., dependency updates)     | *(No Bump)* |
| **`BREAKING CHANGE`** | Specified using `!` in the title (e.g., `feat!:`). | **MAJOR** (e.g., v1.0.0 → v2.0.0) |

### Examples

| Final Commit Title | Result in Release Notes |
| :--- | :--- |
| `feat(users): Added new service to retrieve user data` | **Features:** Added new service to retrieve user data |
| `fix(db): Corrected null constraint error in latest migration` | **Bug Fixes:** Corrected null constraint error in latest migration |
| `chore(deps): Bumped Gradle version to 8.6` | *(Excluded from Release Notes)* |
| `fix(auth)!: Required 'role' header on all requests` | **BREAKING CHANGES:** Required 'role' header on all requests |

***

### 🚀 Release Process

The **`release-please`** action automatically creates or updates a **Release PR** containing the proposed version tag and the `CHANGELOG.md` updates.

**Merging the Release PR** to `main` is the final step that publishes the official GitHub Release and creates the version tag, which should trigger your existing CI/CD for deployment.