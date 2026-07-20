# Testing Axon Code

Axon behaviour spans pure domain decisions, message routing, event processing, transactions, and
serialization. No single test style covers all of those well. Choose the smallest boundary that can
prove the risk being changed.

## Test levels

| Test style | Best for | Does not prove |
|---|---|---|
| Aggregate fixture | Rebuilt state, command decisions, emitted events, rejected commands | Spring wiring, real serialization, database transactions |
| Isolated unit test | Mappers, services, exception handler, individual projection handlers | Processor mode, tokens, full message flow |
| In-memory Spring test | HTTP-to-Axon flow, subscription queries, processor reset/replay and recovery | PostgreSQL types, Flyway controls, JPA transaction behaviour |
| PostgreSQL integration test | Event serialization, constraints, transaction atomicity, concurrency and migrations | Production deployment or external integrations |
| Architecture test | Dependency and message-shape guardrails | Runtime behaviour |

## Aggregate fixture tests

Use `AggregateTestFixture<ApplicationAggregate>` for rules owned by the application consistency
boundary. The fixture follows event-sourcing language directly:

```java
fixture
    .given(applicationCreatedEvent(applicationId))
    .when(command)
    .expectEvents(expectedEvent);
```

For rejection:

```java
fixture
    .given(previousEvents)
    .when(command)
    .expectException(ApplicationVersionConflictException.class)
    .expectNoEvents();
```

### Start from events

Use `given(...)` events to establish aggregate state. Do not instantiate the aggregate and set its
fields directly. The purpose of the test is partly to prove that event-sourcing handlers can rebuild
the state used by the command handler.

Use `givenNoPriorActivity()` for creation. `CREATE_IF_MISSING` handlers should cover both the empty
and existing aggregate cases.

### Inject command-side resources

The application aggregate uses `ApplicationDataStore` during command handling. Register it with:

```java
fixture.registerInjectableResource(applicationDataStore);
```

Mock the current payload and append outcome deliberately. Also test resource failure and assert
`expectNoEvents()`.

### Assert public facts, not implementation

Prefer expected events, result messages, and exceptions over inspecting private fields. If future
commands require rebuilt state, a following-command test can demonstrate that earlier events
restored it correctly.

## Isolated tests

Use ordinary JUnit and Mockito when Axon's runtime is not the behaviour under test:

- request-to-command mapper field coverage;
- `ApplicationExceptionHandler` status and safe Problem Details;
- projection handler database updates;
- group-router command mapping and failure propagation;
- application-data hashing and persistence mapping.

Projection unit tests should cover missing rows, idempotency, reset handlers, emitted subscription
updates, and hydration fallbacks where relevant.

## In-memory Spring tests

`AxonInMemoryConfig` replaces the JPA event and token stores with in-memory implementations while
retaining Spring/Axon wiring. Use these tests for behaviour that a fixture cannot demonstrate:

- the complete HTTP command and query flow;
- subscription query timing;
- tracking processor token progress;
- transient and permanent handler failures;
- projection reset and replay.

The custom in-memory storage engine enforces aggregate sequence uniqueness so concurrency behaviour
is closer to the PostgreSQL event store.

These tests are fast but cannot prove PostgreSQL JSON/`bytea` serialization, Flyway triggers, or
transaction rollback across JPA repositories.

## PostgreSQL integration tests

`PostgresAxonIntegrationTest` uses Testcontainers and the real migrations. Add or extend integration
coverage when changing:

- event or metadata serialization;
- `application_data` and event transaction ordering;
- optimistic locking under concurrent requests;
- event-store uniqueness;
- append-only or retention database controls;
- generated API behaviour dependent on real persistence;
- projection behaviour that uses PostgreSQL-specific columns or queries.

Inspect stored event JSON in these tests to prove that newly separated sensitive fields have not
leaked into the event payload.

## Processor recovery tests

Tracking processors must neither skip a failing event nor corrupt their read model during reset.
Recovery coverage should prove:

- reset handlers clear only their owned projection;
- replay rebuilds all expected rows;
- a transient failure succeeds on retry without event loss;
- a permanent failure prevents the token advancing past the failed event;
- unrelated processors can progress independently.

Do not simulate recovery by directly changing production token rows in a unit test. Use Axon's
processor lifecycle APIs.

## Event compatibility tests

When changing a persisted event contract, add a serialization test containing the old stored JSON
shape. Deserialize or upcast it using the configured Jackson/Axon serializer and prove aggregate and
projection replay still work. A test that constructs only the new Java record cannot detect old
payload incompatibility.

See [Event evolution](event-evolution.md) before changing an event class.

## Commands

```bash
# Unit, fixture, architecture, and in-memory tests
./gradlew :data-access-service-axon:test

# Real PostgreSQL/Testcontainers tests
./gradlew :data-access-service-axon:integrationTest

# Style checks
./gradlew :data-access-service-axon:checkstyleMain
```

The operational guide contains the complete [build and test commands](running-and-operating.md).
