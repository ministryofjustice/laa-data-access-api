# E2E API Tests

End-to-end tests that run against a live instance of the Data Access API. They verify real HTTP responses, validate against the OpenAPI spec, and seed/clean up their own test data via direct JDBC.

## Prerequisites

- Java 21+
- A running instance of the API with a PostgreSQL database
- For local: Docker (for PostgreSQL via `docker-compose.yml`)

## Running Locally

1. Start PostgreSQL:
   ```bash
   docker compose up -d
   ```

2. Start the API:
   ```bash
   cd data-access-service
   ./gradlew bootRun
   ```

3. Run the tests:
   ```bash
   cd data-access-service
   ./gradlew e2e
   ```

## Running Against a Deployed Environment

Pass the environment and/or base URL as system properties:

```bash
./gradlew e2e -Denv=staging
```

```bash
./gradlew e2e -Dbase.url=https://api.staging.example.com
```

System properties and environment variables override file-based config (see `E2eConfig.java` source priority). For deployed environments, set `db.url`, `db.username`, and `db.password` as system properties or env vars.

## How It Works

### Architecture

```
BaseApiTest                    — Setup/teardown shared by all test classes
├── spec (RequestSpecification) — Base URI, headers, content type, OpenAPI filter
├── seeder (TestDataSeeder)     — JDBC connection for inserting/cleaning test data
└── config (E2eConfig)          — Environment-aware config via Owner library

TestDataSeeder                 — Inserts rows directly into PostgreSQL using PreparedStatements
├── Uses entity generators from testUtilities source set (same as integration tests)
├── Tracks created IDs for cleanup
└── Deletes seeded data in @AfterAll (FK cascades handle join tables)

HealthCheckApiTest             — Standalone (no BaseApiTest), no DB needed
```

### Request Flow

Every test that uses `spec` gets automatic **OpenAPI contract validation** via the Atlassian `OpenApiValidationFilter`. This validates both the request and response against `open-api-application-specification.yml` on every call — no extra code needed in individual tests.

### Test Data Seeding

Tests seed their own data via `TestDataSeeder`, which uses the same entity generators as integration tests (`ApplicationEntityGenerator`, `IndividualEntityGenerator`). Data is inserted directly into PostgreSQL via JDBC, bypassing the API, so each endpoint is tested independently.

Multi-step seeding (e.g. `seedApplicationWithIndividual`) is wrapped in a transaction — if any insert fails, the whole operation rolls back.

### Cleanup

`TestDataSeeder` tracks all created application and individual IDs. `BaseApiTest.tearDown()` calls `seeder.cleanup()` which deletes them. Join tables (`linked_individuals`, `domain_events`, etc.) are handled by `ON DELETE CASCADE` foreign keys.

### Configuration

`E2eConfig` uses the [Owner library](https://matteobaccan.github.io/owner/) with this source priority (highest first):

1. System properties (`-Dbase.url=...`)
2. Environment variables
3. `classpath:${env}.properties` (e.g. `staging.properties`)
4. `classpath:local.properties` (defaults)

Config keys: `base.url`, `base.path`, `db.url`, `db.username`, `db.password`, `application.invalidId`, `caseworker.validId`

## Adding a New Endpoint Test

1. Create a new test class extending `BaseApiTest`
2. Seed any required data in `@BeforeAll` using `seeder`
3. Write tests using raw RestAssured chains with `.spec(spec)` — the OpenAPI filter validates automatically
4. No cleanup code needed — `BaseApiTest.tearDown()` handles it

Example:
```java
class MyEndpointApiTest extends BaseApiTest {

  private static String seededId;

  @BeforeAll
  static void seed() throws Exception {
    seededId = seeder.seedApplicationWithIndividual().toString();
  }

  @Test
  void getMyEndpoint_shouldReturn200() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/my-endpoint/{id}", seededId)
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("someField", equalTo("expectedValue"));
  }
}
```

## Test Classes

| Class | Endpoint | Seeds Own Data? |
|-------|----------|-----------------|
| `ApplicationByIdApiTest` | `GET/PATCH /applications/{id}` | Yes — with field-level assertions against seeded entity |
| `ApplicationsApiTest` | `GET /applications` | Yes — tests filters, pagination, sorting |
| `ApplicationHistoryApiTest` | `GET /applications/{id}/history-search` | Yes — seeds domain event |
| `IndividualsApiTest` | `GET /individuals` | Yes — tests type filter |
| `CaseworkersApiTest` | `GET /caseworkers` | No — relies on Flyway seed data |
| `HealthCheckApiTest` | `GET /actuator/health` | No — standalone, no DB required |

## Notes

- Tests run sequentially (`junit-platform.properties` disables parallel execution) — the shared JDBC connection is not thread-safe.
- OpenAPI validation has some spec-drift suppressions downgraded to warnings (see `BaseApiTest`). These should be removed as the spec is brought in line with the API.
- `HealthCheckApiTest` does not extend `BaseApiTest` — it can run without a database connection.
