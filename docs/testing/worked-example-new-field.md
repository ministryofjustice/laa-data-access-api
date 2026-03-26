# Worked Example: Adding a new field to existing functionality

## Scenario

`CreateApplication` needs to store a new optional field — `providerCaseReference` — on the `ApplicationCreateRequest`.
The field should be persisted on `ApplicationEntity` and returned in the `GET /application` response.

---

## What changes

| Layer | Change |
|---|---|
| Model | Add `providerCaseReference` to `ApplicationCreateRequest` |
| Entity | Add `providerCaseReference` column to `ApplicationEntity` |
| Mapper | `toApplicationEntity` reads the new field; `toApplication` writes it to the response |
| Generator | Update `ApplicationEntityGenerator` and `ApplicationCreateRequestGenerator` to populate the field by default |

---

## Test changes required

### 1. Generator (`src/testUtilities`)

Add the new field to the default generator so that all existing tests automatically exercise it without being individually updated:

```java
// ApplicationCreateRequestGenerator.java
public ApplicationCreateRequest createDefault() {
    return ApplicationCreateRequest.builder()
        // ...existing fields...
        .providerCaseReference("PCR-001")   // ← add
        .build();
}

// ApplicationEntityGenerator.java
public ApplicationEntity createDefault() {
    return ApplicationEntity.builder()
        // ...existing fields...
        .providerCaseReference("PCR-001")   // ← add
        .build();
}
```

Adding the field to the generator means every test that already asserts `usingRecursiveComparison()` will now also verify the new field, without any other changes to those tests.

---

### 2. Mapper unit test (`src/test` — `ApplicationMapperTest`)

Add focused tests for the new field in isolation — both present and absent — so that optional behaviour is explicitly pinned:

```java
@Test
void givenApplicationCreateRequestWithProviderCaseReference_whenToApplicationEntity_thenFieldIsMapped() {
    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, builder ->
        builder.providerCaseReference("PCR-999")
    );

    ApplicationEntity entity = applicationMapper.toApplicationEntity(request);

    assertThat(entity.getProviderCaseReference()).isEqualTo("PCR-999");
}

@Test
void givenApplicationCreateRequestWithNullProviderCaseReference_whenToApplicationEntity_thenFieldIsNull() {
    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, builder ->
        builder.providerCaseReference(null)
    );

    ApplicationEntity entity = applicationMapper.toApplicationEntity(request);

    assertThat(entity.getProviderCaseReference()).isNull();
}

@Test
void givenApplicationEntityWithProviderCaseReference_whenToApplication_thenFieldIsMapped() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.providerCaseReference("PCR-999")
    );

    Application response = applicationMapper.toApplication(entity);

    assertThat(response.getProviderCaseReference()).isEqualTo("PCR-999");
}

@Test
void givenApplicationEntityWithNullProviderCaseReference_whenToApplication_thenFieldIsNull() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
        builder.providerCaseReference(null)
    );

    Application response = applicationMapper.toApplication(entity);

    assertThat(response.getProviderCaseReference()).isNull();
}
```

---

### 3. Service unit test (`src/test` — `CreateApplicationTest`)

The existing test `givenNewApplication_whenCreateApplication_thenReturnNewId` calls `verifyThatApplicationSaved`, which uses `ArgumentCaptor` to assert the entity passed to the repository. Because `ApplicationCreateRequestGenerator` now populates `providerCaseReference` by default, and the `verifyThatApplicationSaved` helper already asserts the full entity using `usingRecursiveComparison`, **no new test is needed** — the existing test now covers the new field automatically.

However, if there is any validation on the field (e.g. a format constraint), add a service-level validation test:

```java
@Test
public void givenProviderCaseReferenceExceedsMaxLength_whenCreateApplication_thenThrowValidationException() {
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    String tooLong = "X".repeat(256);
    ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class, builder ->
        builder.providerCaseReference(tooLong)
    );

    Throwable thrown = catchThrowable(() -> serviceUnderTest.createApplication(request));

    assertThat(thrown)
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("providerCaseReference must not exceed 255 characters");

    verify(applicationRepository, never()).save(any());
}
```

---

### 4. Repository integration test (`src/integrationTest` — `ApplicationRepositoryTest`)

The existing `givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual` test calls `persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class)`. Because the generator now populates `providerCaseReference`, the recursive comparison will automatically verify the new column is saved and retrieved correctly.

**No new repository test is needed** unless the field has special database behaviour (e.g. a unique constraint, an index that affects query results).

---

### 5. Controller integration test (`src/integrationTest` — `CreateApplicationTest`)

The existing `givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader` test calls `assertApplicationEqual`, which compares the request against the persisted entity. Add the new field to that assertion:

```java
private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
    // ...existing assertions...
    assertEquals(expected.getProviderCaseReference(), actual.getProviderCaseReference());  // ← add
}
```

Add a test for the controller response to confirm the field is returned by `GET /application`:

```java
@Test
public void givenApplicationWithProviderCaseReference_whenGetApplication_thenResponseIncludesField() throws Exception {
    ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
        builder.providerCaseReference("PCR-123")
               .caseworker(CaseworkerJohnDoe)
    );

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application actualApplication = deserialise(result, Application.class);

    assertOK(result);
    assertSecurityHeaders(result);
    assertEquals("PCR-123", actualApplication.getProviderCaseReference());
}

@Test
public void givenApplicationWithNullProviderCaseReference_whenGetApplication_thenResponseFieldIsNull() throws Exception {
    ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
        builder.providerCaseReference(null)
               .caseworker(CaseworkerJohnDoe)
    );

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    Application actualApplication = deserialise(result, Application.class);

    assertOK(result);
    assertNull(actualApplication.getProviderCaseReference());
}
```

If the field has a `@NotNull` or `@Size` constraint on the request DTO, add it to the parameterised `applicationCreateRequestInvalidDataCases` stream in the controller test alongside the other validation cases.

---

## Summary

| What changed | Test update required | Why |
|---|---|---|
| Generator default | Yes — add field | Cascades coverage into all recursive-comparison assertions for free |
| Mapper | Yes — 4 focused tests (present/null, both directions) | Explicitly pins optional-field behaviour in isolation |
| Service unit test | No (covered by generator + recursive comparison) | Unless the field has its own validation path |
| Repository test | No (covered by generator + recursive comparison) | Unless the field has special DB constraints |
| Controller — create assert | Yes — add field to `assertApplicationEqual` | Pins the full round-trip at the HTTP layer |
| Controller — get response | Yes — 2 tests (present/null) | Confirms the field is returned in the response |

