# Hexagonal Architecture Migration — Effort Estimation

**Status:** Estimate based on the `main` branch baseline, informed by implementation experience on the POC branch.

## Effort Per Use Case

Based on actual time and artefact counts from the `CreateApplicationService` migration on the POC branch.

### A note on the existing structure

On the POC branch, `CreateApplicationService` and `MakeDecisionService` already live in the `service/usecase/` package — they were previously extracted from `ApplicationService` into dedicated classes. This extraction was a positive step: each class now owns a single use case with a clear boundary.

However, the extraction was only structural — the services still depend directly on JPA repositories and entities. The hexagonal migration takes the next step: replacing those concrete infrastructure dependencies with port interfaces so the business logic is fully decoupled.

`ApplicationService` (298 lines) is the remaining catch-all. It contains **6 distinct operations** that share nothing except the `checkIfApplicationExists` helper:

| Method | What it does | Could become |
|---|---|---|
| `getApplication()` | Read an application + its proceedings and decisions | `GetApplicationUseCase` |
| `updateApplication()` | Patch application content and publish an event | `UpdateApplicationUseCase` |
| `createApplicationNote()` | Write a note and publish an event | `CreateApplicationNoteUseCase` |
| `getApplicationNotes()` | Read all notes for an application | `GetApplicationNotesUseCase` |
| `assignCaseworker()` | Assign a caseworker to multiple applications | `AssignCaseworkerUseCase` |
| `unassignCaseworker()` | Remove caseworker from an application | `UnassignCaseworkerUseCase` |

These are unrelated operations bundled into one class. Migrating them to hexagonal would also decompose `ApplicationService` into focused use cases — completing the extraction pattern that was started with `CreateApplicationService` and `MakeDecisionService`.

### Size categories

| Category | Criteria | Estimated effort | Examples |
|---|---|---|---|
| **Small** | ≤3 entity/repo imports, ≤50 lines, single entity | **0.5 day** | `CaseworkerService` (24 lines, 1 import), `CertificateService` (38 lines, 2 imports) |
| **Medium** | 3–6 entity/repo imports, 50–200 lines, some relationships | **1–2 days** | `IndividualsService` (94 lines, 4 imports), `ProceedingsService` (40 lines, 2 imports), `ApplicationSummaryService` (178 lines, 4 imports), individual operations within `ApplicationService` |
| **Large** | 6+ entity/repo imports, 200+ lines, complex multi-entity orchestration | **2–3 days** | `MakeDecisionService` (249 lines, 11 imports) |

### Current service inventory

| Service | Lines | Entity/repo imports | New domain models needed | Size | Est. effort |
|---|---|---|---|---|---|
| `CreateApplicationService` | 183 | 0 (migrated on POC branch) | ✅ Done | — | ✅ Complete |
| `MakeDecisionService` | 249 | 11 | `Decision`, `MeritsDecision`, `Proceeding`, `Certificate` | Large | 2–3 days |
| `ApplicationService` (6 operations) | 298 | 9 | Reuses existing + `Note`, `Caseworker` | 6 × Small/Medium | 3–5 days (incremental) |
| `ApplicationSummaryService` | 178 | 4 | `ApplicationSummary` | Medium | 1–2 days |
| `DomainEventService` | 248 | 3 | None (stays as adapter infrastructure) | Medium | 1 day |
| `IndividualsService` | 94 | 4 | Reuses `Individual` | Medium | 1 day |
| `CertificateService` | 38 | 2 | `Certificate` (likely done by MakeDecision first) | Small | 0.5 day |
| `CaseworkerService` | 24 | 1 | `Caseworker` | Small | 0.5 day |
| `ProceedingsService` | 40 | 2 | Reuses `Proceeding` | Small | 0.5 day |

### Total estimated effort

| Phase | Services | Effort |
|---|---|---|
| Phase 1 (proof of concept on POC branch) | `CreateApplicationService` | ✅ Complete |
| Phase 2 (next use case) | `MakeDecisionService` | 2–3 days |
| Phase 3 (decompose ApplicationService) | `ApplicationService` → 6 focused use cases | 3–5 days (1 PR per operation) |
| Phase 4 (medium services) | `ApplicationSummaryService`, `DomainEventService`, `IndividualsService` | 3–4 days |
| Phase 5 (small services) | `CertificateService`, `CaseworkerService`, `ProceedingsService` | 1–2 days |
| **Total** | **All services from the `main` baseline** | **~10–15 days** |

> **Note:** Later phases benefit from reusable infrastructure (domain models, adapters, mapper) created in earlier phases. The estimates above already account for this — Phase 5 services are trivially small because their domain models and adapters will already exist.
>
> Phase 3 can be done **one operation at a time**. Each operation in `ApplicationService` can be extracted to its own use case in a separate PR. The remaining operations stay in `ApplicationService` until they are migrated — the class naturally shrinks with each PR.

### What takes the time?

| Activity | % of effort | Notes |
|---|---|---|
| Defining domain model + ports | ~15% | Straightforward — mirror the entity fields minus JPA annotations |
| Creating adapters | ~20% | Mostly mechanical mapping code |
| Refactoring the service | ~25% | Replace types and method calls. Logic stays the same. |
| Updating the controller + factory | ~10% | Thin — just wire up the new types |
| Updating tests | ~20% | Legacy bridge method minimises this, but tests should be reviewed |
| Code review + verification | ~10% | Full test suite must pass |

### What doesn't need effort?

- No database migrations
- No API contract changes
- No new dependencies (Gradle/Maven)
- No CI/CD pipeline changes
- No infrastructure changes


