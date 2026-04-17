# Worked Example: Adding new behaviour to existing functionality

## Scenario

`MakeDecision` currently handles `REFUSED` decisions. We need to add support for `GRANTED` decisions with the following behaviour:

- When `overallDecision` is `GRANTED`, a `certificate` must be present on the request — throw a `ValidationException` if it is absent.
- A `CertificateEntity` is saved against the application.
- A domain event is published for the `GRANTED` decision, and includes the certificate.

---

## What changes

| Layer | Change |
|---|---|
| Validation | New rule: `GRANTED` requires a `certificate` on the request |
| Service | Branch on `overallDecision == GRANTED`; save `CertificateEntity`; publish domain event |
| Entity | New `CertificateEntity`; new `CertificateRepository` |
| Generator | New `CertificateContentGenerator` and `CertificateEntityGenerator` in `src/testUtilities` |

---

## Why GRANTED needs its own full test set

`GRANTED` shares the same entry point, authorisation check, application lookup, and proceeding validation as `REFUSED`. It would be tempting to write only the two tests that are unique to `GRANTED` (validation failure and certificate persistence) and rely on the existing `REFUSED` tests for everything else.

The problem is that the shared code paths are only proven to work _for `REFUSED`_. If the `REFUSED` path is later refactored — say, the `REFUSED` path is separated from the `GRANTED` path and the application lookup or proceeding validation is moved into a helper — the `REFUSED` tests will still pass, but the `GRANTED` path could silently break if it was not wired up the same way. The only protection against that is tests that exercise the `GRANTED` path through the same scenarios.

The rule of thumb: **any test that exists for `REFUSED` should have a direct equivalent for `GRANTED`**, with the differences between the two outcomes explicitly asserted in each test.

---

## Test changes required

### 1. Generator (`src/testUtilities`)

Write generators before anything else so both source sets can use them:

```java
// CertificateContentGenerator.java
public class CertificateContentGenerator extends BaseGenerator<CertificateContent, CertificateContent.Builder> {
    public CertificateContentGenerator() {
        super(CertificateContent::toBuilder, CertificateContent.Builder::build);
    }

    @Override
    public CertificateContent createDefault() {
        return CertificateContent.builder()
            .certificateNumber("TESTCERT001")
            .issueDate("2026-03-03")
            .validUntil("2027-03-03")
            .build();
    }
}

// CertificateEntityGenerator.java
public class CertificateEntityGenerator extends BaseGenerator<CertificateEntity, CertificateEntity.CertificateEntityBuilder> {
    public CertificateEntityGenerator() {
        super(CertificateEntity::builder, CertificateEntity.CertificateEntityBuilder::build);
    }

    @Override
    public CertificateEntity createDefault() {
        return CertificateEntity.builder()
            .applicationId(UUID.randomUUID())
            .certificateContent(Map.of("certificateNumber", "TESTCERT001"))
            .createdBy(UUID.randomUUID().toString())
            .updatedBy(UUID.randomUUID().toString())
            .build();
    }
}
```

---

### 2. Repository integration test (`src/integrationTest` — `CertificateRepositoryTest`)

Write this before the service logic. It proves the entity and its Flyway migration are correct, and gives the service and controller tests a reliable foundation to build on.

```java
@Test
public void givenSaveOfExpectedCertificate_whenGetCalled_expectedAndActualAreEqual() {
    // given
    ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    CertificateEntity expected = persistedDataGenerator.createAndPersist(CertificateEntityGenerator.class,
        builder -> builder.applicationId(applicationEntity.getId())
    );
    clearCache();

    // when
    CertificateEntity actual = certificateRepository.findById(expected.getId()).orElse(null);

    // then
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actual);
    assertThat(expected.getCreatedAt()).isNotNull();
    assertThat(expected.getModifiedAt()).isNotNull();
}
```

---

### 3. Service unit test (`src/test` — `MakeDecisionForApplicationTest`)

For each existing `REFUSED` scenario, add a direct `GRANTED` equivalent. The table below maps every existing test to the new test that mirrors it, and calls out what differs in the assertion.

| Existing REFUSED test | New GRANTED equivalent | Difference in assertion |
|---|---|---|
| `givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenDecisionSaved` | `givenGrantedDecisionWithTwoProceedings_whenAssignDecision_thenDecisionAndCertificateSaved` | `GRANTED` domain event type; certificate saved |
| `givenApplicationAndExistingDecision_whenAssignDecision_thenDecisionUpdated` | `givenGrantedDecisionAndExistingDecision_whenAssignDecision_thenDecisionUpdatedAndCertificateSaved` | Certificate saved; `GRANTED` domain event |
| `givenApplicationAndExistingDecisionAndNewProceeding_whenAssignDecision_thenDecisionUpdated` | `givenGrantedDecisionAndExistingDecisionAndNewProceeding_whenAssignDecision_thenDecisionUpdatedAndCertificateSaved` | Certificate saved |
| `givenApplicationWithNoCaseworker_whenAssignDecision_thenThrowResourceNotFoundException` | `givenGrantedDecisionAndNoCaseworker_whenAssignDecision_thenThrowResourceNotFoundException` | Same exception; no certificate saved |
| `givenNoApplication_whenAssignDecision_thenThrowResourceNotFoundException` | `givenGrantedDecisionAndNoApplication_whenAssignDecision_thenThrowResourceNotFoundException` | Same exception; no certificate saved |
| `givenNoProceeding_whenAssignDecision_thenThrowResourceNotFoundException` | `givenGrantedDecisionAndNoProceeding_whenAssignDecision_thenThrowResourceNotFoundException` | Same exception; no certificate saved |
| `givenProceedingNotLinkedToApplication_whenMakeDecision_thenThrowResourceNotFoundException` | `givenGrantedDecisionAndProceedingNotLinkedToApplication_whenMakeDecision_thenThrowResourceNotFoundException` | Same exception; no certificate saved |
| `givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenThrowResourceNotFoundExceptionWithAllIds` | `givenGrantedDecisionAndProceedingsNotFoundAndNotLinked_whenMakeDecision_thenThrowResourceNotFoundExceptionWithAllIds` | Same exception; no certificate saved |
| _(no equivalent — `REFUSED`-specific validation)_ | `givenGrantedDecisionWithoutCertificate_whenMakeDecision_thenValidationExceptionThrown` | Unique to `GRANTED` |

The key tests are shown in full below.

**Happy path — `GRANTED` with certificate, decision and domain event saved:**

```java
@Test
void givenGrantedDecisionWithTwoProceedings_whenAssignDecision_thenDecisionAndCertificateSaved()
        throws JacksonException {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID proceedingOneId = UUID.randomUUID();
    UUID proceedingTwoId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class,
        builder -> builder.id(caseworkerId));

    CertificateContent certificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);
    Map<String, Object> certificateData = objectMapper.convertValue(certificateContent, Map.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(EventHistory.builder().eventDescription("granted event").build())
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingOneId, MeritsDecisionStatus.GRANTED, "justification 1", "reason 1"),
                createMakeDecisionProceedingDetails(proceedingTwoId, MeritsDecisionStatus.GRANTED, "justification 2", "reason 2")
            ))
            .certificate(certificateData)
    );

    ApplicationEntity applicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.id(applicationId).applicationContent(new HashMap<>(Map.of("test", "content"))).caseworker(caseworker)
    );

    DomainEventEntity expectedDomainEvent = DomainEventEntity.builder()
        .applicationId(applicationId)
        .caseworkerId(caseworkerId)
        .type(DomainEventType.APPLICATION_MAKE_DECISION_GRANTED)
        .data(objectMapper.writeValueAsString(
            MakeDecisionGrantedDomainEventDetails.builder()
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .eventDescription("granted event")
                .request(objectMapper.writeValueAsString(makeDecisionRequest))
                .build()
        ))
        .build();

    ProceedingEntity proceedingOne = DataGenerator.createDefault(ProceedingsEntityGenerator.class,
        builder -> builder.id(proceedingOneId).applicationId(applicationId));
    ProceedingEntity proceedingTwo = DataGenerator.createDefault(ProceedingsEntityGenerator.class,
        builder -> builder.id(proceedingTwoId).applicationId(applicationId));

    setSecurityContext(TestConstants.Roles.CASEWORKER);
    when(proceedingRepository.findAllById(List.of(proceedingOneId, proceedingTwoId)))
        .thenReturn(List.of(proceedingOne, proceedingTwo));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    serviceUnderTest.makeDecision(applicationId, makeDecisionRequest);

    // then
    verify(applicationRepository, times(1)).findById(applicationId);
    verify(applicationRepository, times(2)).save(any(ApplicationEntity.class));
    verifyThatDomainEventSaved(domainEventRepository, objectMapper, expectedDomainEvent, 1);
    verifyDecisionSavedCorrectly(makeDecisionRequest, applicationEntity, 2);

    ArgumentCaptor<CertificateEntity> certificateCaptor = ArgumentCaptor.forClass(CertificateEntity.class);
    verify(certificateRepository, times(1)).save(certificateCaptor.capture());
    CertificateEntity savedCertificate = certificateCaptor.getValue();
    assertThat(savedCertificate.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCertificate.getCertificateContent()).isEqualTo(certificateData);
    assertThat(savedCertificate.getCreatedBy()).isEqualTo(caseworkerId.toString());
    assertThat(savedCertificate.getUpdatedBy()).isEqualTo(caseworkerId.toString());
}
```

**GRANTED-specific validation path — no certificate:**

```java
@Test
void givenGrantedDecisionWithoutCertificate_whenMakeDecision_thenValidationExceptionThrown() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")
            ))
            .certificate(null)
    );

    ApplicationEntity applicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.id(applicationId).caseworker(caseworker)
    );

    ProceedingEntity proceedingEntity = DataGenerator.createDefault(ProceedingsEntityGenerator.class,
        builder -> builder.id(proceedingId).applicationId(applicationId));

    setSecurityContext(TestConstants.Roles.CASEWORKER);
    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of(proceedingEntity));
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    // when
    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest));

    // then
    assertThat(thrown).isInstanceOf(ValidationException.class);
    assertThat(((ValidationException) thrown).errors())
        .contains("The Make Decision request must contain a certificate when overallDecision is GRANTED");

    verify(certificateRepository, never()).save(any());
    verify(applicationRepository, never()).save(any());
    verify(domainEventRepository, never()).save(any());
}
```

**Error path equivalents — these confirm that shared error handling works for `GRANTED` independently of `REFUSED`:**

```java
@Test
void givenGrantedDecisionAndNoApplication_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder.overallDecision(DecisionStatus.GRANTED)
    );
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No application found with id: " + applicationId);

    verify(certificateRepository, never()).save(any());
}

@Test
void givenGrantedDecisionAndNoCaseworker_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationEntity applicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.id(applicationId).caseworker(null)
    );
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder.overallDecision(DecisionStatus.GRANTED)
    );
    setSecurityContext(TestConstants.Roles.CASEWORKER);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));

    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Caseworker not found for application id: " + applicationId);

    verify(certificateRepository, never()).save(any());
}

@Test
void givenGrantedDecisionAndNoProceeding_whenAssignDecision_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class);

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, requestBuilder ->
        requestBuilder
            .overallDecision(DecisionStatus.GRANTED)
            .proceedings(List.of(
                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "justification", "reason")
            ))
    );
    ApplicationEntity applicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.id(applicationId).caseworker(caseworker)
    );

    setSecurityContext(TestConstants.Roles.CASEWORKER);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(applicationEntity));
    when(proceedingRepository.findAllById(List.of(proceedingId))).thenReturn(List.of());

    Throwable thrown = catchThrowable(() -> serviceUnderTest.makeDecision(applicationId, makeDecisionRequest));

    assertThat(thrown)
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No proceeding found with id: " + proceedingId);

    verify(certificateRepository, never()).save(any());
}
```

The remaining error path equivalents (`proceedingNotLinked`, `proceedingsNotFoundAndNotLinked`, `existingDecisionUpdated`) follow exactly the same pattern: copy the `REFUSED` test, change `overallDecision` to `GRANTED`, add `verify(certificateRepository, never()).save(any())` to every error path assertion.

---

### 4. Controller integration test (`src/integrationTest` — `ApplicationMakeDecisionTest`)

The same principle applies: every HTTP-level test that exists for `REFUSED` needs a `GRANTED` equivalent. The table below maps them.

| Existing REFUSED controller test | New GRANTED equivalent | Difference |
|---|---|---|
| `givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionSaved` | `givenGrantedDecisionWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionAndCertificateSaved` | Assert certificate in DB; `GRANTED` domain event |
| `givenMakeDecisionRequestWithExistingContentAndNewContent_whenAssignDecision_thenReturnNoContent_andDecisionUpdated` | `givenGrantedDecisionWithExistingDecision_whenAssignDecision_thenReturnNoContent_andDecisionUpdatedAndCertificateSaved` | Certificate saved |
| `givenMakeDecisionRequestWithMissingJustification_whenAssignDecision_thenReturnBadRequest` | `givenGrantedDecisionWithNoCertificate_whenAssignDecision_thenReturnBadRequest` | Different validation message; `GRANTED` specific |
| `givenNoApplication_whenAssignDecisionApplication_thenReturnNotFoundAndMessage` | `givenGrantedDecisionAndNoApplication_whenAssignDecision_thenReturnNotFoundAndMessage` | Same response body; `GRANTED` overallDecision in request |
| `givenApplicationWithNoCaseworker_whenAssignDecisionApplication_thenReturnNotFoundAndMessage` | `givenGrantedDecisionAndNoCaseworker_whenAssignDecision_thenReturnNotFoundAndMessage` | Same response body |
| `givenProceedingsNotFoundAndNotLinkedToApplication_whenMakeDecision_thenReturnNotFoundWithAllIds` | `givenGrantedDecisionAndProceedingsNotFoundAndNotLinked_whenMakeDecision_thenReturnNotFoundWithAllIds` | Same response body |

The key tests are shown in full below.

**Happy path → 204 + certificate and domain event persisted:**

```java
@Test
public void givenGrantedDecisionWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionAndCertificateSaved()
        throws Exception {
    ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
        builder.caseworker(CaseworkerJohnDoe)
    );
    ProceedingEntity proceedingOne = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
        builder -> builder.applicationId(applicationEntity.getId()));
    ProceedingEntity proceedingTwo = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
        builder -> builder.applicationId(applicationEntity.getId()));

    CertificateContent expectedCertificateContent = DataGenerator.createDefault(CertificateContentGenerator.class);
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder ->
        builder
            .overallDecision(DecisionStatus.GRANTED)
            .eventHistory(EventHistory.builder().eventDescription("granted event").build())
            .proceedings(List.of(
                createMakeDecisionProceeding(proceedingOne.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1"),
                createMakeDecisionProceeding(proceedingTwo.getId(), MeritsDecisionStatus.GRANTED, "justification 2", "reason 2")
            ))
            .certificate(objectMapper.convertValue(expectedCertificateContent, Map.class))
            .autoGranted(false)
    );

    // when
    HarnessResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNoContent(result);

    verifyDecisionSavedCorrectly(applicationEntity.getId(), makeDecisionRequest);

    domainEventAsserts.assertDomainEventsCreatedForApplications(
        List.of(applicationEntity),
        CaseworkerJohnDoe.getId(),
        DomainEventType.APPLICATION_MAKE_DECISION_GRANTED,
        makeDecisionRequest.getEventHistory()
    );

    // Assert certificate persisted with every expected field
    List<CertificateEntity> certificates = certificateRepository.findAll();
    assertThat(certificates.size()).isEqualTo(1);
    CertificateEntity certificate = certificates.get(0);
    assertThat(certificate.getApplicationId()).isEqualTo(applicationEntity.getId());
    assertThat(certificate.getCertificateContent().get("certificateNumber")).isEqualTo("TESTCERT001");
    assertThat(certificate.getCreatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
    assertThat(certificate.getUpdatedBy()).isEqualTo(CaseworkerJohnDoe.getId().toString());
    assertThat(certificate.getCreatedAt()).isNotNull();
    assertThat(certificate.getModifiedAt()).isNotNull();
}
```

**Validation failure → 400:**

```java
@Test
public void givenGrantedDecisionWithNoCertificate_whenAssignDecision_thenReturnBadRequest() throws Exception {
    ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
        builder.caseworker(CaseworkerJohnDoe)
    );
    ProceedingEntity proceedingEntity = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class,
        builder -> builder.applicationId(applicationEntity.getId()));

    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder ->
        builder
            .overallDecision(DecisionStatus.GRANTED)
            .proceedings(List.of(
                createMakeDecisionProceeding(proceedingEntity.getId(), MeritsDecisionStatus.GRANTED, "justification 1", "reason 1")
            ))
            .certificate(null)
    );

    HarnessResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationEntity.getId());

    assertSecurityHeaders(result);
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());

    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    List<String> errors = (List<String>) problemDetail.getProperties().get("errors");
    assertThat(errors).contains("The Make Decision request must contain a certificate when overallDecision is GRANTED");

    // Confirm nothing persisted
    List<CertificateEntity> certificates = certificateRepository.findAll();
    assertThat(certificates.size()).isEqualTo(0);
    assertThat(decisionRepository.count()).isEqualTo(0);
}
```

**Error path equivalents — copy the `REFUSED` test, set `overallDecision` to `GRANTED`, assert no certificate in DB:**

```java
@Test
public void givenGrantedDecisionAndNoApplication_whenAssignDecision_thenReturnNotFoundAndMessage() throws Exception {
    UUID applicationId = UUID.randomUUID();
    MakeDecisionRequest makeDecisionRequest = DataGenerator.createDefault(ApplicationMakeDecisionRequestGenerator.class, builder ->
        builder
            .overallDecision(DecisionStatus.GRANTED)
            .certificate(objectMapper.convertValue(DataGenerator.createDefault(CertificateContentGenerator.class), Map.class))
    );

    HarnessResult result = patchUri(TestConstants.URIs.ASSIGN_DECISION, makeDecisionRequest, applicationId);

    assertSecurityHeaders(result);
    assertNotFound(result);
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("No application found with id: " + applicationId, problemDetail.getDetail());

    assertThat(certificateRepository.findAll()).isEmpty();
}
```

The remaining error path equivalents follow the same pattern. For each one: use `overallDecision(DecisionStatus.GRANTED)` and a valid certificate on the request (so the `GRANTED` validation does not fire before the error under test), assert the expected HTTP response and message, and assert `certificateRepository.findAll().isEmpty()`.

---

## What the test suite proves

| Assertion | Where |
|---|---|
| `GRANTED` without certificate → `ValidationException` with exact message | Service unit test |
| `GRANTED` without certificate → HTTP 400 with correct `ProblemDetail` | Controller integration test |
| `GRANTED` with certificate → `CertificateEntity` saved with all correct fields | Service unit test (ArgumentCaptor) + controller integration test (repository read-back) |
| `GRANTED` domain event published with correct type and payload | Service unit test + controller integration test |
| All `GRANTED` error paths (`no application`, `no caseworker`, `no proceeding`, `proceeding not linked`) → correct exception / HTTP response | Service unit test + controller integration test |
| No certificate saved on any error path | Asserted in every error test for both layers |
| `REFUSED` behaviour is unchanged | Existing service and controller tests — no changes needed |
| `CertificateEntity` fields round-trip through JPA correctly | Repository integration test |

---

## What not to test again

Authentication (`401`) and authorisation (`403`) are already covered by the existing `MakeDecision` controller tests and do not need new tests for `GRANTED`. The security annotation fires before the `overallDecision` value is read, so those paths are shared unconditionally.

The service name header validation tests are similarly shared — one set covers both behaviours.
