# Plan: New `AwaitingApplication` Aggregate — Async Create with Subscription Query

## Goal

Create a **new, standalone** `AwaitingApplication` aggregate that uses a **tracking**
event processor (async) for its projection, but still returns **201 Created** with a
`Location` header on the POST by using Axon's `QueryUpdateEmitter` + subscription query
to wait for the projection to confirm it has written before responding.

This design is useful when the projection must run outside the command's unit of work
(e.g. separate DB, read-replica, or Elasticsearch) but you still want a synchronous HTTP
response.

---

## Why this design works

| Concern | How it is handled |
|---|---|
| Async projection confirmation | Controller opens a **subscription query** for the specific `applyApplicationId` before dispatching the command; the projection emits an update via `QueryUpdateEmitter` when it writes, which unblocks the controller |
| No race condition | The subscription is registered **before** `sendAndWait` so no update can be missed even if the tracking processor is very fast |
| Timeout handling | If no update arrives within the configured timeout the controller returns **202 Accepted** — the client can poll `GET /api/v0/awaiting-applications/{id}` |
| Uniqueness of `applyApplicationId` | Same as `SynchronousApplication`: `@AggregateIdentifier` backed by the event store unique constraint |
| Idempotency | `@CreationPolicy(CREATE_IF_MISSING)` + null-check on the identifier field |

---

## Thread / transaction flow

```
POST /api/v0/awaiting-applications
  │
  ├─ CreateAwaitingApplicationCommandMapper.toCommand()
  │
  ├─ queryGateway.subscriptionQuery(          ← registered BEFORE command dispatch
  │       FindAwaitingApplicationByIdQuery,
  │       initialResultType = Optional<AwaitingApplicationReadModel>,
  │       updateType       = AwaitingApplicationReadModel)
  │
  ├─ CommandGateway.sendAndWait()             ← stores event, returns applyApplicationId
  │    └─ AwaitingApplicationAggregate#handle()
  │         └─ apply(AwaitingApplicationCreatedEvent)
  │
  ├─ [tracking processor — separate thread]
  │    └─ AwaitingApplicationProjection#on()
  │         ├─ repository.save(readModel)
  │         └─ queryUpdateEmitter.emit(       ← unblocks the subscription
  │                 FindAwaitingApplicationByIdQuery.class,
  │                 q -> q.applyApplicationId().equals(event.applyApplicationId()),
  │                 readModel)
  │
  ├─ subscriptionResult.updates().next()
  │    .block(Duration.ofSeconds(10))         ← waits for update or times out
  │
  ├─ if update received within timeout
  │    └─ ResponseEntity.created(location)   ← 201
  └─ if timeout
       └─ ResponseEntity.accepted()          ← 202 — client should poll GET
```

---

## Package layout (all new files)

```
command/awaitingapplication/
    CreateAwaitingApplicationCommand.java
    AwaitingApplicationAggregate.java
    AwaitingApplicationCreatedEvent.java
    AwaitingApplicationIndividual.java
    AwaitingApplicationProceeding.java

query/awaitingapplication/
    AwaitingApplicationReadModel.java
    AwaitingApplicationReadRepository.java
    AwaitingApplicationProjection.java
    FindAwaitingApplicationByIdQuery.java

controller/awaitingapplication/
    AwaitingApplicationCommandController.java
    AwaitingApplicationQueryController.java
    CreateAwaitingApplicationCommandMapper.java
    GetAwaitingApplicationResponseMapper.java

config/
    AwaitingApplicationConfig.java   (timeout Duration bean)

resources/db/migration/
    V7__create_awaiting_application_current_state.sql
```

---

## Step 1 — Command: `CreateAwaitingApplicationCommand`

**File:** `command/awaitingapplication/CreateAwaitingApplicationCommand.java`

Identical structure to `CreateSynchronousApplicationCommand`.

```java
public record CreateAwaitingApplicationCommand(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<AwaitingApplicationIndividual> individuals,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    String applicationType) {

  @TargetAggregateIdentifier
  public UUID applyApplicationId() {
    return UUID.fromString(applicationContent.get("id").toString());
  }
}
```

---

## Step 2 — Event: `AwaitingApplicationCreatedEvent`

**File:** `command/awaitingapplication/AwaitingApplicationCreatedEvent.java`

Identical structure to `SynchronousApplicationCreatedEvent` — same fields, new package.

---

## Step 3 — Value types

`AwaitingApplicationIndividual` and `AwaitingApplicationProceeding` — identical structure
to their `SynchronousApplication` counterparts.

---

## Step 4 — Aggregate: `AwaitingApplicationAggregate`

**File:** `command/awaitingapplication/AwaitingApplicationAggregate.java`

Identical to `SynchronousApplicationAggregate` — `@AggregateIdentifier`,
`@CreationPolicy(CREATE_IF_MISSING)`, returns `UUID`.

---

## Step 5 — Read model: `AwaitingApplicationReadModel`

**File:** `query/awaitingapplication/AwaitingApplicationReadModel.java`

```java
@Entity
@Table(name = "awaiting_application_current_state")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AwaitingApplicationReadModel {
  @Id
  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  // ... same fields as SynchronousApplicationReadModel ...
}
```

---

## Step 6 — Repository

**File:** `query/awaitingapplication/AwaitingApplicationReadRepository.java`

```java
public interface AwaitingApplicationReadRepository
    extends JpaRepository<AwaitingApplicationReadModel, UUID> {}
```

---

## Step 7 — Query record

**File:** `query/awaitingapplication/FindAwaitingApplicationByIdQuery.java`

```java
public record FindAwaitingApplicationByIdQuery(UUID applyApplicationId) {}
```

---

## Step 8 — Projection: `AwaitingApplicationProjection`

**File:** `query/awaitingapplication/AwaitingApplicationProjection.java`

The critical addition is the `QueryUpdateEmitter` call inside `on()`. This must be
called **after** `repository.save()` so the subscriber receives the persisted state.

The `@ProcessingGroup` must be configured as `mode: tracking` (Step 13) — this is what
makes the projection run asynchronously on a separate thread.

```java
@Component
@ProcessingGroup("awaiting-application-projection")
public class AwaitingApplicationProjection {

  private final AwaitingApplicationReadRepository repository;
  private final QueryUpdateEmitter queryUpdateEmitter;

  public AwaitingApplicationProjection(
      AwaitingApplicationReadRepository repository,
      QueryUpdateEmitter queryUpdateEmitter) {
    this.repository = repository;
    this.queryUpdateEmitter = queryUpdateEmitter;
  }

  @QueryHandler
  public Optional<AwaitingApplicationReadModel> handle(FindAwaitingApplicationByIdQuery query) {
    return repository.findById(query.applyApplicationId());
  }

  @EventHandler
  public void on(AwaitingApplicationCreatedEvent event) {
    AwaitingApplicationReadModel saved = repository.save(
        AwaitingApplicationReadModel.builder()
            // ... map all fields from event ...
            .build());

    // Emit update AFTER save — unblocks any waiting subscription queries
    queryUpdateEmitter.emit(
        FindAwaitingApplicationByIdQuery.class,
        query -> query.applyApplicationId().equals(event.applyApplicationId()),
        saved);
  }

  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
```

> `QueryUpdateEmitter` is auto-wired from the Axon Spring Boot auto-configuration.
> No additional bean declaration is needed.

---

## Step 9 — Config: `AwaitingApplicationConfig`

**File:** `config/AwaitingApplicationConfig.java`

Declares the timeout duration used by the controller as a named bean so it can be
overridden in tests.

```java
@Configuration
public class AwaitingApplicationConfig {

  /** Maximum time to wait for the tracking projection to confirm write before returning 202. */
  @Bean
  @Qualifier("awaitingApplicationProjectionTimeout")
  Duration awaitingApplicationProjectionTimeout() {
    return Duration.ofSeconds(10);
  }
}
```

---

## Step 10 — Command mapper

**File:** `controller/awaitingapplication/CreateAwaitingApplicationCommandMapper.java`

Identical structure to `CreateSynchronousApplicationCommandMapper`.

---

## Step 11 — Controllers

### `AwaitingApplicationCommandController`

**Route:** `POST /api/v0/awaiting-applications`

The subscription query is opened **before** `sendAndWait` using a `try-with-resources`
block to guarantee the subscription is always closed.

```java
@PostMapping
public ResponseEntity<Void> createApplication(
    @RequestHeader("X-Service-Name") ServiceName serviceName,
    @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
        int schemaVersion,
    @Valid @RequestBody ApplicationCreateRequest request) {

  CreateAwaitingApplicationCommand command = commandMapper.toCommand(request, schemaVersion);
  UUID applyApplicationId = command.applyApplicationId();

  // 1. Open subscription BEFORE dispatching so no update can be missed.
  SubscriptionQueryResult<Optional<AwaitingApplicationReadModel>, AwaitingApplicationReadModel>
      subscription =
          queryGateway.subscriptionQuery(
              new FindAwaitingApplicationByIdQuery(applyApplicationId),
              ResponseTypes.optionalInstanceOf(AwaitingApplicationReadModel.class),
              ResponseTypes.instanceOf(AwaitingApplicationReadModel.class));

  try {
    // 2. Dispatch command — stores the event.
    commandGateway.sendAndWait(command);

    // 3. Wait for the tracking projection to emit the update.
    AwaitingApplicationReadModel projected =
        subscription.updates().next().block(projectionTimeout);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(applyApplicationId)
            .toUri();

    if (projected != null) {
      // Projection confirmed write — return 201 Created.
      return ResponseEntity.created(location).build();
    } else {
      // Projection did not confirm within timeout — return 202 Accepted.
      return ResponseEntity.accepted().location(location).build();
    }

  } finally {
    subscription.close();
  }
}
```

> `SubscriptionQueryResult` is `AutoCloseable` in Axon 4.x. Using `try-finally` ensures
> the Flux subscription is always cancelled and no memory leak occurs.

### `AwaitingApplicationQueryController`

**Route:** `GET /api/v0/awaiting-applications/{id}`

Standard query dispatch — identical to `SynchronousApplicationQueryController`.

### `GetAwaitingApplicationResponseMapper`

Mirrors `GetSynchronousApplicationResponseMapper`.

---

## Step 12 — Register entity in `AxonJpaConfig`

```java
@EntityScan(
    basePackageClasses = {
      // ... existing entries ...
      AwaitingApplicationReadModel.class   // ADD
    })
```

---

## Step 13 — `application.yml` — add tracking processor

```yaml
axon:
  eventhandling:
    processors:
      # ... existing entries unchanged ...
      awaiting-application-projection:
        mode: tracking        # async — runs on its own thread pool
```

> Because the processor is tracking, Axon creates a `token_entry` row in the token store
> for `awaiting-application-projection`. No migration change is needed — the
> `token_entry` table already exists from `V1__create_axon_event_store.sql`.

---

## Step 14 — Migration `V7__create_awaiting_application_current_state.sql`

```sql
CREATE TABLE awaiting_application_current_state (
    apply_application_id     UUID         PRIMARY KEY,
    status                   VARCHAR(255) NOT NULL,
    laa_reference            VARCHAR(255) NOT NULL,
    application_content      JSONB        NOT NULL,
    individuals              JSONB        NOT NULL,
    schema_version           INTEGER      NOT NULL,
    application_type         VARCHAR(255) NOT NULL,
    submitted_at             TIMESTAMPTZ  NOT NULL,
    office_code              VARCHAR(255),
    used_delegated_functions BOOLEAN,
    category_of_law          VARCHAR(255),
    matter_type              VARCHAR(255),
    proceedings              JSONB        NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL
);
```

---

## Step 15 — Tests to create

| Test class | What to verify |
|---|---|
| `AwaitingApplicationAggregateTest` | Same fixture pattern as `SynchronousApplicationAggregateTest` |
| `AwaitingApplicationProjectionTest` | `on()` saves the read model **and** calls `queryUpdateEmitter.emit()` with the correct predicate and payload |
| `AwaitingApplicationCommandControllerTest` | Mock `CommandGateway` and `QueryGateway`; subscription update arrives before timeout → assert 201 + Location; subscription times out → assert 202 + Location |
| `CreateAwaitingApplicationCommandMapperTest` | Same as `CreateSynchronousApplicationCommandMapperTest` |
| `CreateAwaitingApplicationInMemoryTest` | Full in-process test; assert 201 and projection populated; assert `awaiting-application-projection` is a `TrackingEventProcessor` |
| `AwaitingApplicationPostgresIntegrationTest` | `POST` → 201 + Location (projection confirms within timeout); `GET {location}` → 200 + body |

### Key test detail — `AwaitingApplicationCommandControllerTest` timeout path

```java
@Test
void givenProjectionDoesNotRespond_whenPost_thenReturns202() {
    // subscription.updates().next().block(timeout) returns null on timeout
    SubscriptionQueryResult<?, ?> slowSubscription = mock(SubscriptionQueryResult.class);
    when(slowSubscription.updates()).thenReturn(Flux.never());
    when(queryGateway.subscriptionQuery(...)).thenReturn(slowSubscription);
    when(commandGateway.sendAndWait(any())).thenReturn(applyApplicationId);

    // Use a very short timeout bean override for the test
    ResponseEntity<Void> response = controller.createApplication(...);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    verify(slowSubscription).close();  // subscription must always be closed
}
```

---

## Design trade-offs vs `SynchronousApplication`

| | `SynchronousApplication` | `AwaitingApplication` |
|---|---|---|
| Processor mode | `subscribing` (same thread) | `tracking` (separate thread) |
| Projection DB isolation | Same transaction as command | Own transaction |
| Response guarantee | Always 201 | 201 if projection fast, 202 on timeout |
| Failure isolation | Projection failure rolls back command | Command succeeds even if projection stalls |
| Re-drive / replay | Full replay possible | Full replay possible |
| Suitable for | Simple writes, low latency | Cross-DB writes, external sinks |

