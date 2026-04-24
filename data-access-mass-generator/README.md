# data-access-mass-generator

A standalone Spring Boot console application that seeds a running PostgreSQL instance test data for the 
LAA Data Access API. It generates applications, caseworkers, individuals,
proceedings, and — for a configurable proportion of applications — full decision records
(merits decisions, overall decisions, and certificates).

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
  - [Generation pipeline](#generation-pipeline)
  - [Decision coverage](#decision-coverage)
  - [Batching and memory management](#batching-and-memory-management)
- [Configuration](#configuration)
- [Module Structure](#module-structure)
- [Architecture Notes](#architecture-notes)
  - [Why no changes to data-access-service](#why-no-changes-to-data-access-service)
  - [Why JDBC batching is limited](#why-jdbc-batching-is-limited)
  - [linked_individuals join table](#linked_individuals-join-table)
  - [DecisionEntity cascade workaround](#decisionentity-cascade-workaround)

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java | 25+ on `PATH` |
| Docker | Running with the Postgres container up (`docker compose up -d`) |
| Postgres | Reachable at `localhost:5432` (default); schema already migrated by Flyway |

---

## Quick Start

```bash
# Start the database (if not already running)
docker compose up -d

# Generate 100 applications (default)
./scripts/run-mass-generator.sh

# Generate a custom number of applications
./scripts/run-mass-generator.sh 5000
```

The script:
1. Validates the `COUNT` argument and checks Postgres is reachable.
2. Builds the fat JAR (`./gradlew :data-access-mass-generator:bootJar`).
3. Runs `java -jar <jar> <COUNT>`.

At the end it prints a summary:

```
========================================
       Mass Generation Report
========================================
  Records generated  : 250000
  Decided applications: 98457 (39%)
  Total time         : 29 min 14 sec
  Throughput         : 142.5 records/sec
========================================
```

(based on an actual run of 250k applications with 40% decisions on a local Postgres instance).

To run directly without the script (e.g. from IntelliJ or after a manual build):

```bash
./gradlew :data-access-mass-generator:bootJar
java -jar data-access-mass-generator/build/libs/data-access-mass-generator-*.jar 1000
```

Database connection details can be overridden with environment variables (see [Configuration](#configuration)).

---

## How It Works

### Generation pipeline

For each application record the runner executes the following steps in order:

```
1.  FullJsonGenerator.createDefault()
      └─ Builds a fully-populated ApplicationContent (all nested fields, datafaker values)

2.  Pick random CaseworkerEntity  from pre-generated pool (100)
    Pick random IndividualEntity  from pre-generated pool (1 000)

3.  Derive entity columns from content
      └─ categoryOfLaw, matterType  ← lead proceeding
      └─ usedDelegatedFunctions     ← any proceeding with delegated functions
      └─ officeCode, laaReference, submittedAt ← content top-level fields

4.  Persist ApplicationEntity (mirrors ApplicationContentParserService + ApplicationMapper)

4a. INSERT into linked_individuals via native query (bypasses CascadeType.PERSIST)

5.  Persist ProceedingEntity list — one per proceeding in the content
      └─ Mirrors ProceedingsService.saveProceedings / ProceedingMapper.toProceedingEntity

6.  [~40% of applications] Persist decision block
      └─ 6a. MeritsDecisionEntity per proceeding (GRANTED or REFUSED)
      └─ 6b. DecisionEntity (GRANTED / PARTIALLY_GRANTED / REFUSED)
      └─ 6c. CertificateEntity if overall decision == GRANTED
      └─ 6d. application.decision_id ← DecisionEntity (mirrors ApplicationService.makeDecision)

7.  flushAndClear() every 500 applications to bound Hibernate session memory
```

The caseworker and individual pools are pre-generated in two `saveAllAndFlush` calls at startup,
keeping the number of pool-related round-trips constant regardless of the total count.

### Decision coverage

A `DECISION_RATE` constant (`0.4`) controls what proportion of applications get a decision record.
The overall decision is weighted:

| Decision | Approximate frequency |
|---|---|
| `REFUSED` | 50% of decided applications |
| `GRANTED` | 30% of decided applications |
| `PARTIALLY_GRANTED` | 20% of decided applications |

Both constants are at the top of `MassDataGeneratorRunner` and are easy to adjust before a run.

A `CertificateEntity` is only created when the overall decision is `GRANTED`, matching the runtime
behaviour of `ApplicationService.makeDecision()`.

### Batching and memory management

Every entity in `data-access-service` uses `GenerationType.IDENTITY` with a UUID column. PostgreSQL
generates the UUID via `uuid_generate_v4()` rather than a sequence, so Hibernate **cannot batch
`IDENTITY` inserts** — it must round-trip to the DB after every row to read back the generated key.
The `reWriteBatchedInserts`, `batch_size`, `order_inserts` settings in `application.yml` are correct
and harmless, but they have no effect on throughput for IDENTITY-mapped entities.

What is done to improve throughput within these constraints:

- **Proceedings** are accumulated into a list and persisted with a single `saveAllAndFlush(list)` per
  application rather than one `saveAndFlush` per proceeding.
- **Pool entities** (caseworkers, individuals) are generated with `createAndPersistMultipleRandom` which
  similarly uses a single `saveAllAndFlush`.
- **Hibernate session** is flushed and cleared every `BATCH_SIZE` (500) applications to keep the
  first-level cache from growing unboundedly during large runs.

---

## Configuration

All settings live in `src/main/resources/application.yml`. The most commonly overridden values:

| Property | Default | Purpose |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/laa_data_access_api?reWriteBatchedInserts=true` | Database JDBC URL |
| `spring.datasource.username` | `laa_user` | Database username |
| `spring.datasource.password` | `laa_password` | Database password |
| `spring.jpa.show-sql` | `false` | Set to `true` to log every SQL statement |
| `logging.level.root` | `WARN` | Root log level |

Override at runtime using standard Spring externalized configuration:

```bash
java -jar data-access-mass-generator-*.jar 1000 \
  --spring.datasource.url=jdbc:postgresql://my-host:5432/laa_data_access_api \
  --spring.datasource.username=myuser \
  --spring.datasource.password=mypass
```

Or via environment variables (`DB_HOST` / `DB_PORT` are also read by the shell script):

```bash
DB_HOST=my-host DB_PORT=5432 ./scripts/run-mass-generator.sh 1000
```

---

## Module Structure

```
data-access-mass-generator/
├── build.gradle
├── plans/                                      ← github co-pilot plans used to implement this
│   ├── 01-mass-generator-module.md
│   ├── 02-full-json-generator.md
│   ├── 03-generate-data-with-full-json.md
│   ├── 04-batching-strategy.md
│   └── 05-decision-entities.md
└── src/main/
    ├── java/.../massgenerator/
    │   ├── MassGeneratorApp.java               ← Spring Boot entry point (non-web)
    │   ├── MassDataGeneratorRunner.java        ← CommandLineRunner — main generation loop
    │   └── generator/
    │       ├── PersistedDataGenerator.java     ← thin JPA persistence wrapper
    │       ├── LinkedIndividualWriter.java     ← native INSERT into linked_individuals
    │       └── application/
    │           ├── FullJsonGenerator.java      ← root ApplicationContent generator
    │           ├── FullOfficeGenerator.java
    │           ├── FullOfficeScheduleGenerator.java
    │           ├── FullProviderGenerator.java
    │           ├── FullApplicantGenerator.java
    │           ├── FullApplicantAddressGenerator.java
    │           ├── FullBenefitCheckResultGenerator.java
    │           ├── FullLegalFrameworkMeritsTaskListGenerator.java
    │           ├── FullStateMachineGenerator.java
    │           ├── FullMeansGenerator.java
    │           ├── FullCfeSubmissionGenerator.java
    │           ├── FullApplicationMeritsGenerator.java
    │           ├── FullProceedingGenerator.java
    │           ├── FullProceedingMeritsGenerator.java
    │           ├── FullScopeLimitationGenerator.java
    │           ├── FullOpposableGenerator.java
    │           ├── FullOpponentDetailsGenerator.java
    │           ├── FullMeritsDecisionGenerator.java
    │           └── FullCertificateGenerator.java
    └── resources/
        ├── application.yml
        └── application-example.json
```

---

## Architecture Notes

### Why no changes to `data-access-service`

All new code is confined to `data-access-mass-generator`. The module depends on
`data-access-service` main output and its `testUtilities` compiled classes (for the
`BaseGenerator` / `DataGenerator` infrastructure), but adds nothing back. This keeps the
production service untouched and the mass generator removable without side effects.

### Why JDBC batching is limited

See [Batching and memory management](#batching-and-memory-management) above. The short version:
`GenerationType.IDENTITY` with a UUID column forces a round-trip per insert. There is no way to use SEQUENCE
for UUID generation in Postgres, so Hibernate's batching features cannot be leveraged for the main entities. 
The generator instead focuses on batching at the application level (persisting all proceedings for an application in one call) and on memory management (flushing and clearing the session every 500 applications).

### `linked_individuals` join table

`ApplicationEntity.individuals` uses `CascadeType.PERSIST`. Writing to it normally would cause
Hibernate to try to re-persist `IndividualEntity` objects that already exist in the DB, throwing
a duplicate-key exception. `LinkedIndividualWriter` bypasses this by issuing a native
`INSERT INTO linked_individuals` directly via `EntityManager.createNativeQuery`. It lives in its
own `@Component` so that Spring's AOP proxy honours `@Transactional` (self-invocation would
silently bypass the proxy).

### `DecisionEntity` cascade workaround

`DecisionEntity.meritsDecisions` has `CascadeType.PERSIST`. Passing already-persisted
`MeritsDecisionEntity` objects in the constructor and calling `saveAndFlush` would cause Hibernate
to attempt to re-persist them as detached entities, throwing
`InvalidDataAccessApiUsageException: Detached entity passed to persist`.

The workaround in `MassDataGeneratorRunner`:
1. Persist `DecisionEntity` *without* the merits set (no cascade triggered).
2. Call `decision.setMeritsDecisions(merits)` after the decision has an ID.
3. Call `PersistedDataGenerator.mergeDecision(decision)`, which uses `EntityManager.merge()` before
   `saveAndFlush` — treating the already-persisted merits as managed rather than new.

`reattach()` is called on `proceedingBatch` and `app` before the decision block to re-associate
them with the current Hibernate session after a potential `flushAndClear()`, preventing the same
detached-entity error on the `@OneToOne proceeding` FK in `MeritsDecisionEntity`.

