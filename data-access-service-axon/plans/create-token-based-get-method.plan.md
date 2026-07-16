# Plan: New `TokenApplication` Aggregate — Async Create with Persistent Token Polling

## Goal

Create a **new, standalone** `TokenApplication` aggregate whose POST endpoint returns a
**persistent event-store position token** in the response. The client passes this token
to the GET endpoint; the server compares it against the tracking processor's current
position to determine whether the projection has caught up, returning either **200 OK**
with the body or **202 Accepted** (still processing).

This design avoids any blocking wait on the POST path and gives clients a precise,
durable signal to poll against — useful for fire-and-forget POST callers and for
long-lived async pipelines.

---

## Why this design works

| Concern | How it is handled |
|---|---|
| No blocking on POST | Command is dispatched and returns immediately; the event store sequence number is read from the stored event and returned in `X-Event-Position` header |
| Client-driven polling | Client passes `?minPosition={N}` on GET; server checks if the tracking token has advanced past `N` |
| Definitive "caught up" check | Axon's `TokenStore.fetchToken("token-application-projection", segment)` returns the processor's current `TrackingToken`; its position is compared with `N` |
| Uniqueness / idempotency | Same as `SynchronousApplication` — `@AggregateIdentifier` + `@CreationPolicy(CREATE_IF_MISSING)` |
| Staleness safety | A token value is a monotonic sequence number; clients that cache and re-use it always see at least that projection state |

---

## Token mechanics

Axon's JPA event store assigns a monotonically increasing **global sequence number** to
every domain event it stores (the `global_index` column of `domain_event_entry`). The
tracking token for a processor records the highest `global_index` it has consumed.

After `sendAndWait` the controller retrieves the global index of the just-stored event
via `EmbeddedEventStore` / `StreamableMessageSource`. A simpler approach that avoids
coupling to the event store internals: the aggregate's `@CommandHandler` returns the
`applyApplicationId`, and the controller then queries the event store for the sequence
number of the event whose `aggregateIdentifier = applyApplicationId`:

```
SELECT global_index
FROM   axon.domain_event_entry
WHERE  aggregate_identifier = :applyApplicationId
ORDER  BY sequence_number DESC
LIMIT  1;
```

This is done through a thin `TokenApplicationEventPositionRepository` (a JPA projection
repository or a `JdbcTemplate` query) rather than coupling directly to Axon internals.

On GET, the controller calls:

```
TokenStore.fetchToken("token-application-projection", segment=0)
```

`GapAwareTrackingToken` and `GlobalSequenceTrackingToken` both implement `position()`
which returns the highest consumed `global_index`. If `token.position() >= minPosition`
the projection has written the row.

---

## Thread / transaction flow

```
POST /api/v0/token-applications
  │
  ├─ CreateTokenApplicationCommandMapper.toCommand()
  │
  ├─ CommandGateway.sendAndWait()                   ← stores event, returns applyApplicationId
  │    └─ TokenApplicationAggregate#handle()
  │         └─ apply(TokenApplicationCreatedEvent)
  │
  ├─ eventPositionRepository.findPositionFor(        ← reads global_index of stored event
  │       applyApplicationId)
  │
  └─ ResponseEntity.accepted()
         .header("X-Event-Position", position)       ← 202 + Location + X-Event-Position
         .location(location)
         .build()


GET /api/v0/token-applications/{id}?minPosition=42
  │
  ├─ tokenStore.fetchToken(                          ← reads processor's current token
  │       "token-application-projection", segment=0)
  │
  ├─ if token.position() >= minPosition
  │    └─ queryGateway.query(FindTokenApplicationByIdQuery)
  │         └─ 200 OK + body
  │
  └─ if token.position() < minPosition              ← projection not yet caught up
       └─ 202 Accepted  (or 404 if read model absent and client omits minPosition)
```

---

## Package layout (all new files)

```
command/tokenapplication/
    CreateTokenApplicationCommand.java
    TokenApplicationAggregate.java
    TokenApplicationCreatedEvent.java
    TokenApplicationIndividual.java
    TokenApplicationProceeding.java

query/tokenapplication/
    TokenApplicationReadModel.java
    TokenApplicationReadRepository.java
    TokenApplicationProjection.java
    FindTokenApplicationByIdQuery.java
    TokenApplicationEventPositionRepository.java   ← reads global_index

controller/tokenapplication/
    TokenApplicationCommandController.java
    TokenApplicationQueryController.java
    CreateTokenApplicationCommandMapper.java
    GetTokenApplicationResponseMapper.java

config/
    TokenApplicationConfig.java

resources/db/migration/
    V8__create_token_application_current_state.sql
```

---

## Step 1 — Command: `CreateTokenApplicationCommand`

Identical structure to `CreateSynchronousApplicationCommand`.

---

## Step 2 — Event: `TokenApplicationCreatedEvent`

Identical structure to `SynchronousApplicationCreatedEvent`.

---

## Step 3 — Value types

`TokenApplicationIndividual` and `TokenApplicationProceeding` — identical structure to
their `SynchronousApplication` counterparts.

---

## Step 4 — Aggregate: `TokenApplicationAggregate`

Identical to `SynchronousApplicationAggregate`.

---

## Step 5 — Read model: `TokenApplicationReadModel`

**File:** `query/tokenapplication/TokenApplicationReadModel.java`

```java
@Entity
@Table(name = "token_application_current_state")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TokenApplicationReadModel {
  @Id
  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  // ... same fields as SynchronousApplicationReadModel ...
}
```

---

## Step 6 — Repository

```java
public interface TokenApplicationReadRepository
    extends JpaRepository<TokenApplicationReadModel, UUID> {}
```

---

## Step 7 — Query record

```java
public record FindTokenApplicationByIdQuery(UUID applyApplicationId) {}
```

---

## Step 8 — Event position repository

**File:** `query/tokenapplication/TokenApplicationEventPositionRepository.java`

Provides the `global_index` of the most recent event stored for a given aggregate. This
is the value returned to the client as `X-Event-Position`.

```java
@Repository
public class TokenApplicationEventPositionRepository {

  private final JdbcTemplate jdbcTemplate;

  public TokenApplicationEventPositionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Returns the highest global_index stored for the given aggregate identifier, or -1 if none.
   * The query targets the schema used by the JPA event store (configured via spring.flyway.default-schema).
   */
  public long findPositionFor(UUID aggregateIdentifier) {
    Long position = jdbcTemplate.queryForObject(
        "SELECT global_index FROM axon.domain_event_entry "
            + "WHERE aggregate_identifier = ? "
            + "ORDER BY sequence_number DESC LIMIT 1",
        Long.class,
        aggregateIdentifier.toString());
    return position == null ? -1L : position;
  }
}
```

> The schema prefix (`axon.`) matches the Flyway default schema configured in
> `application.yml`. Override via `${spring.flyway.default-schema}` if needed.

---

## Step 9 — Projection: `TokenApplicationProjection`

**File:** `query/tokenapplication/TokenApplicationProjection.java`

Standard tracking projection — no `QueryUpdateEmitter` needed.

```java
@Component
@ProcessingGroup("token-application-projection")
public class TokenApplicationProjection {

  private final TokenApplicationReadRepository repository;

  // ... constructor ...

  @QueryHandler
  public Optional<TokenApplicationReadModel> handle(FindTokenApplicationByIdQuery query) {
    return repository.findById(query.applyApplicationId());
  }

  @EventHandler
  public void on(TokenApplicationCreatedEvent event) {
    repository.save(TokenApplicationReadModel.builder()
        // ... map all fields ...
        .build());
  }

  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
```

---

## Step 10 — Config: `TokenApplicationConfig`

**File:** `config/TokenApplicationConfig.java`

Exposes the processor name and segment as constants to avoid magic strings being
duplicated between the controller, projection, and tests.

```java
@Configuration
public class TokenApplicationConfig {

  public static final String PROCESSOR_NAME = "token-application-projection";
  public static final int SEGMENT = 0;
}
```

---

## Step 11 — Command mapper

`CreateTokenApplicationCommandMapper` — identical structure to
`CreateSynchronousApplicationCommandMapper`.

---

## Step 12 — Controllers

### `TokenApplicationCommandController`

**Route:** `POST /api/v0/token-applications`

Returns **202 Accepted** (not 201) because the projection has not yet confirmed write.
The `Location` header points to the resource and `X-Event-Position` carries the token.

```java
@PostMapping
public ResponseEntity<Void> createApplication(
    @RequestHeader("X-Service-Name") ServiceName serviceName,
    @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1") @Min(1)
        int schemaVersion,
    @Valid @RequestBody ApplicationCreateRequest request) {

  CreateTokenApplicationCommand command = commandMapper.toCommand(request, schemaVersion);
  UUID applyApplicationId;
  try {
    applyApplicationId = commandGateway.sendAndWait(command);
  } catch (AggregateStreamCreationException | ConcurrencyException e) {
    DuplicateApplyApplicationIdException ex =
        new DuplicateApplyApplicationIdException(command.applyApplicationId());
    ex.initCause(e);
    throw ex;
  }

  long eventPosition = eventPositionRepository.findPositionFor(applyApplicationId);

  URI location =
      ServletUriComponentsBuilder.fromCurrentRequest()
          .path("/{id}")
          .buildAndExpand(applyApplicationId)
          .toUri();

  return ResponseEntity.accepted()
      .location(location)
      .header("X-Event-Position", String.valueOf(eventPosition))
      .build();
}
```

### `TokenApplicationQueryController`

**Route:** `GET /api/v0/token-applications/{id}?minPosition={N}`

`minPosition` is optional. When supplied, the server checks the tracking token before
returning the projection.

```java
@GetMapping("/{id}")
public ResponseEntity<ApplicationResponse> getTokenApplicationById(
    @PathVariable UUID id,
    @RequestParam(value = "minPosition", required = false) Long minPosition) {

  if (minPosition != null) {
    TrackingToken token =
        tokenStore.fetchToken(TokenApplicationConfig.PROCESSOR_NAME,
                              TokenApplicationConfig.SEGMENT);
    long processorPosition = token == null ? -1L : token.position().orElse(-1L);
    if (processorPosition < minPosition) {
      // Projection has not yet consumed the event — tell the client to retry.
      return ResponseEntity.accepted().<ApplicationResponse>build();
    }
  }

  TokenApplicationReadModel application =
      queryGateway
          .query(
              new FindTokenApplicationByIdQuery(id),
              ResponseTypes.optionalInstanceOf(TokenApplicationReadModel.class))
          .join()
          .orElseThrow(
              () -> new ResourceNotFoundException("No token application found with ID: " + id));

  return ResponseEntity.ok(responseMapper.toResponse(application));
}
```

Key points:
- `tokenStore` is the same `TokenStore` bean used by Axon (already configured in
  `AxonJpaConfig`).
- `token.position()` returns `OptionalLong` — use `.orElse(-1L)` to treat an
  uninitialized processor as position `-1`.
- When `minPosition` is omitted, the controller behaves like a normal query endpoint
  (useful for clients that do not care about projection lag).

### `GetTokenApplicationResponseMapper`

Mirrors `GetSynchronousApplicationResponseMapper`.

---

## Step 13 — Register entity in `AxonJpaConfig`

```java
@EntityScan(
    basePackageClasses = {
      // ... existing entries ...
      TokenApplicationReadModel.class   // ADD
    })
```

---

## Step 14 — `application.yml` — add tracking processor

```yaml
axon:
  eventhandling:
    processors:
      # ... existing entries unchanged ...
      token-application-projection:
        mode: tracking
```

---

## Step 15 — Migration `V8__create_token_application_current_state.sql`

```sql
CREATE TABLE token_application_current_state (
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

## Step 16 — Tests to create

| Test class | What to verify |
|---|---|
| `TokenApplicationAggregateTest` | Same fixture pattern as `SynchronousApplicationAggregateTest` |
| `TokenApplicationProjectionTest` | `on()` saves the correct read model |
| `TokenApplicationEventPositionRepositoryTest` | Mock `JdbcTemplate`; returns correct position; returns -1 when no row |
| `TokenApplicationCommandControllerTest` | Mock `CommandGateway` and `TokenApplicationEventPositionRepository`; assert 202 + Location + `X-Event-Position` header |
| `TokenApplicationQueryControllerTest` | (a) `minPosition` not supplied → calls query, returns 200; (b) `minPosition` supplied, processor caught up → 200; (c) processor behind → 202 |
| `CreateTokenApplicationCommandMapperTest` | Same as `CreateSynchronousApplicationCommandMapperTest` |
| `CreateTokenApplicationInMemoryTest` | Full in-process test; assert POST returns 202 + `X-Event-Position`; assert GET with `minPosition` eventually returns 200 after polling; assert `token-application-projection` is `TrackingEventProcessor` |
| `TokenApplicationPostgresIntegrationTest` | `POST` → 202 + `X-Event-Position=N`; poll `GET /{id}?minPosition=N` until 200; assert body |

### Key test detail — polling GET in `CreateTokenApplicationInMemoryTest`

```java
@Test
void givenValidRequest_whenPostThenPollGet_thenEventuallyReturns200() throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> postResponse =
        restTemplate.postForEntity("/api/v0/token-applications",
            new HttpEntity<>(validCreateApplicationRequest(applyApplicationId, ...), headers()),
            Void.class);

    assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    long position = Long.parseLong(postResponse.getHeaders().getFirst("X-Event-Position"));

    // Poll until projection catches up (tracking processor is async even in-memory)
    String getUrl = "/api/v0/token-applications/" + applyApplicationId + "?minPosition=" + position;
    ResponseEntity<ApplicationResponse> getResponse = awaitGet(getUrl);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().getApplicationId()).isEqualTo(applyApplicationId);
}

private ResponseEntity<ApplicationResponse> awaitGet(String url) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            return new ResponseEntity<>(
                objectMapper.readValue(response.getBody(), ApplicationResponse.class),
                response.getHeaders(), response.getStatusCode());
        }
        Thread.sleep(50);
    }
    throw new AssertionError("GET did not return 200 within timeout for: " + url);
}
```

---

## Design trade-offs vs other patterns

| | `SynchronousApplication` | `AwaitingApplication` | `TokenApplication` |
|---|---|---|---|
| Processor mode | `subscribing` | `tracking` | `tracking` |
| POST response | 201 immediately | 201 or 202 with timeout | 202 always |
| Client complexity | None | None | Must store + pass token |
| Blocking on POST | Command thread only | Command thread + projection wait | None |
| Projection lag observable | No | No (hidden by timeout) | Yes — via token |
| Suitable for | Low-latency same-DB writes | Cross-DB, tolerable latency | High-throughput async, client controls retry |

---

## `TokenStore` injection note

The `TokenStore` bean is already declared in `AxonJpaConfig`. Inject it directly into
the controller:

```java
private final TokenStore tokenStore;

public TokenApplicationQueryController(
    QueryGateway queryGateway,
    GetTokenApplicationResponseMapper responseMapper,
    TokenStore tokenStore) { ... }
```

No additional configuration is required.

