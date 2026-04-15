# Implementation Prompt: Migrate `CreateApplicationService` to Hexagonal Architecture

## Context

You are working on `laa-data-access-api`, a Spring Boot 4 / Gradle multi-module project (Java 25, Lombok, MapStruct, Spring Data JPA, Jackson). The module `data-access-service` contains the application logic.

The task is to migrate `CreateApplicationService` to follow hexagonal (ports & adapters) architecture. **No API behaviour may change.** All existing tests must continue to pass.

Read these files first to understand the current state:

- `docs/DSTEW-1361/01-hexagonal-architecture-overview.md` — general context and target package structure
- `docs/DSTEW-1361/02-create-application-service-migration-steps.md` — detailed step-by-step plan with checklist

Then read these source files to understand the code being changed:

- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/usecase/CreateApplicationService.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/controller/ApplicationController.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/repository/ApplicationRepository.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/mapper/ApplicationMapper.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/model/ApplicationContent.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/model/ParsedAppContentDetails.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/model/LinkedApplication.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationContentParserService.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/DomainEventService.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ProceedingsService.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/validation/PayloadValidationService.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/validation/ApplicationValidations.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/security/AllowApiCaseworker.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/IndividualEntity.java`
- `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/mapper/IndividualMapper.java`
- `data-access-service/src/test/java/uk/gov/justice/laa/dstew/access/service/application/CreateApplicationTest.java`

---

## Instructions

Follow the plan in `02-create-application-service-migration-steps.md` exactly. Execute each phase in order. Here is the work broken into specific implementation tasks:

### Task 1: Create Domain Model Classes

Create the following new files under `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/domain/model/`:

1. **`Application.java`** — A plain Java class (no `@Entity`, no `@Table`, no `@Column`, no `@Schema`). Use Lombok `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`. Include all fields that `CreateApplicationService` reads or writes on `ApplicationEntity`: `id` (UUID), `version` (Long), `status`, `laaReference`, `officeCode`, `applicationContent` (Map<String, Object>), `individuals` (Set<Individual>), `schemaVersion` (Integer), `createdAt` (Instant), `modifiedAt` (Instant), `applyApplicationId` (UUID), `submittedAt` (Instant), `usedDelegatedFunctions` (Boolean), `categoryOfLaw`, `matterType`, `linkedApplications` (Set<Application>). Include the `addLinkedApplication(Application)` method.

2. **`Individual.java`** — Mirror the fields from `IndividualEntity` that `CreateApplicationService` / the mapper needs, as a plain Lombok-annotated class.

For `ApplicationStatus`, `CategoryOfLaw`, and `MatterType`: these already exist in `model/` and are simple enums. For now, keep them where they are and import them into the domain classes. Do not move them — other code depends on their current location.

### Task 2: Create Port Interfaces

Create the following new files:

1. **`domain/port/inbound/CreateApplicationUseCase.java`** — Interface with a single method: `UUID createApplication(CreateApplicationCommand command)`.

2. **`domain/port/inbound/CreateApplicationCommand.java`** — A `record` (or Lombok `@Builder` record) with fields: `status` (ApplicationStatus), `laaReference` (String), `applicationContent` (Map<String, Object>), `individuals` (Set<Individual>), `parsedContent` (ParsedAppContentDetails), `linkedApplications` (List — containing lead/associated IDs). This carries everything the use case needs, pre-validated.

3. **`domain/port/outbound/ApplicationPersistencePort.java`** — Interface with methods: `Application save(Application)`, `boolean existsByApplyApplicationId(UUID)`, `Optional<Application> findByApplyApplicationId(UUID)`, `List<Application> findAllByApplyApplicationIdIn(List<UUID>)`.

4. **`domain/port/outbound/ProceedingsPersistencePort.java`** — Interface with method: `void saveProceedings(Map<String, Object> applicationContent, UUID applicationId)`. The Map is the raw application content — the adapter will deserialise it.

5. **`domain/port/outbound/DomainEventPort.java`** — Interface with method: `void publishApplicationCreated(Application application, CreateApplicationCommand command)`.

### Task 3: Create Outbound Adapters

Create the following new files under `data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/adapter/outbound/`:

1. **`persistence/ApplicationPersistenceAdapter.java`** — A `@Component` that implements `ApplicationPersistencePort`. Inject `ApplicationRepository` and a mapper. Implement each method by delegating to the repository and mapping between `Application` (domain) and `ApplicationEntity` (JPA). For the mapper, add `toDomain(ApplicationEntity)` and `toEntity(Application)` methods — these can live in `ApplicationMapper` or in a new `DomainEntityMapper` interface. Use whichever approach is simpler. The `toEntity` method must handle the `individuals` set mapping (reuse `IndividualMapper` logic).

2. **`persistence/ProceedingsPersistenceAdapter.java`** — A `@Component` that implements `ProceedingsPersistencePort`. Inject the existing `ProceedingsService` (or `ProceedingRepository` + `ProceedingMapper` directly). Convert the raw `Map<String, Object>` back to `ApplicationContent` using `ObjectMapper`, then delegate to the existing proceedings-saving logic.

3. **`event/DomainEventAdapter.java`** — A `@Component` that implements `DomainEventPort`. Inject `DomainEventService`. In `publishApplicationCreated()`, map the domain `Application` back to `ApplicationEntity` (for the existing `DomainEventService` API) and map the `CreateApplicationCommand` back to `ApplicationCreateRequest`. This is a pragmatic bridge — the adapter translates domain types to the types `DomainEventService` currently expects. Add a TODO comment noting that `DomainEventService` should be refactored to accept domain types in a future ticket.

### Task 4: Update the Controller

Edit `ApplicationController.java`:

1. Replace the `CreateApplicationService` field with `CreateApplicationUseCase`.
2. In the `createApplication()` method:
   - Add `@AllowApiCaseworker` to this method.
   - Call `payloadValidationService.convertAndValidate(req.getApplicationContent(), ApplicationContent.class)` here (inject `PayloadValidationService` into the controller if not already present).
   - Build a `CreateApplicationCommand` from the request and validated content.
   - Call `createApplicationUseCase.createApplication(command)`.
3. Add a private helper method `mapToCommand(ApplicationCreateRequest, ApplicationContent)` that constructs the command. Use the `ApplicationContentParserService` to get `ParsedAppContentDetails` and include it in the command. Inject `ApplicationContentParserService` into the controller.

### Task 5: Refactor `CreateApplicationService`

Edit `CreateApplicationService.java`:

1. Add `implements CreateApplicationUseCase`.
2. Replace all constructor dependencies:
   - `ApplicationRepository` → `ApplicationPersistencePort`
   - `ApplicationMapper` → remove (no longer needed — the persistence adapter handles mapping)
   - `DomainEventService` → `DomainEventPort`
   - `ProceedingsService` → `ProceedingsPersistencePort`
   - `PayloadValidationService` → remove (moved to controller)
   - `ApplicationContentParserService` → remove (moved to controller, or keep if needed for domain logic)
   - `ApplicationValidations` → keep (it's domain validation)
3. Change the method signature: `public UUID createApplication(final CreateApplicationCommand command)`.
4. Remove `@AllowApiCaseworker` (now on the controller).
5. Keep `@Transactional`.
6. Rewrite the method body to work with domain `Application` objects via port interfaces. The logic remains the same — only the types and method calls change.
7. Update all private methods (`setValuesFromApplicationContent`, `checkForDuplicateApplication`, `linkToLeadApplicationIfApplicable`, `getLeadApplication`, `getLeadApplicationId`, `checkIfAllAssociatedApplicationsExist`) to operate on domain types.

### Task 6: Update Tests

1. **Update `CreateApplicationTest.java`**: Change mocks from `ApplicationRepository` to `ApplicationPersistencePort`, etc. Update any assertions that reference `ApplicationEntity` to use domain `Application`. Ensure all existing test scenarios still pass.

2. **Create `CreateApplicationServiceUnitTest.java`** (optional, but recommended): A pure Mockito test that verifies business logic by mocking port interfaces. No Spring context. Test cases:
   - Happy path: creates and returns ID.
   - Duplicate application: throws `ValidationException`.
   - Linked application: lead found → linked correctly.
   - Linked application: lead not found → throws `ResourceNotFoundException`.
   - Associated applications missing → throws `ResourceNotFoundException`.

### Task 7: Verify

After all changes:

1. Run `./gradlew build` — all compilation must succeed.
2. Run `./gradlew test integrationTest -x checkstyleMain` — all existing tests must pass.
3. Verify no file in `domain/` imports from `entity/`, `repository/`, `adapter/`, `controller/`, or `security/`.
4. Verify `CreateApplicationService` does not import from `entity/`, `repository/`, or `security/`.

---

## Constraints

- **Do not change any existing API contract** — request/response shapes, status codes, error messages, and headers must remain identical.
- **Do not restructure packages beyond what is described** — the existing `entity/`, `repository/`, `mapper/`, `model/`, `controller/` packages stay where they are. Only add new `domain/` and `adapter/` packages.
- **Do not refactor `DomainEventService`, `ProceedingsService`, or `ApplicationMapper`** beyond adding adapter bridge methods. Those are out of scope for this ticket.
- **Do not modify `MakeDecisionService`** or any other use case. Only `CreateApplicationService` is in scope.
- **Keep `@Transactional` on the use case** for now. Moving transaction management is a future refinement.
- **Use Lombok** for all new classes (consistent with the codebase).
- **All new classes must have Javadoc** on the class and public methods (consistent with the codebase).

