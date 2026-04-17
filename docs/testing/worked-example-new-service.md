# Worked Example: Adding a new service with new entities

## Scenario

We need to add a **Document** service. A document belongs to an application. Caseworkers can upload document metadata (filename, type, uploaded-at timestamp) and retrieve all documents for a given application.

This is a greenfield slice: new entity, new repository, new service, new controller, new mapper, new endpoint.

---

## What is being built

| Component | Details |
|---|---|
| `DocumentEntity` | `id` (UUID, generated), `applicationId` (UUID), `filename` (String), `documentType` (String), `uploadedAt` (Instant), `createdAt` (Instant, generated), `modifiedAt` (Instant, generated) |
| `DocumentRepository` | `JpaRepository<DocumentEntity, UUID>`; custom finder `findAllByApplicationId(UUID)` |
| `UploadDocumentRequest` | DTO: `filename` (not null), `documentType` (not null) |
| `Document` (response DTO) | `id`, `applicationId`, `filename`, `documentType`, `uploadedAt` |
| `DocumentMapper` | `toDocumentEntity(UploadDocumentRequest, UUID applicationId)` → `DocumentEntity`; `toDocument(DocumentEntity)` → `Document` |
| `DocumentService` | `uploadDocument(UUID applicationId, UploadDocumentRequest)` → `UUID`; `getDocumentsForApplication(UUID applicationId)` → `List<Document>` |
| `DocumentController` | `POST /applications/{id}/documents` → 201; `GET /applications/{id}/documents` → 200 |

---

## Order of work

Build and test in this order so that each layer is proven before the next layer relies on it:

1. Generator
2. Repository integration test
3. Mapper unit test
4. Service unit test
5. Controller integration test

---

## Step 1 — Generators (`src/testUtilities`)

Write generators before any test, so both the unit and integration source sets can use them:

```java
// DocumentEntityGenerator.java
public class DocumentEntityGenerator extends BaseGenerator<DocumentEntity, DocumentEntity.DocumentEntityBuilder> {

    public DocumentEntityGenerator() {
        super(DocumentEntity::builder, DocumentEntity.DocumentEntityBuilder::build);
    }

    @Override
    public DocumentEntity createDefault() {
        return DocumentEntity.builder()
            .applicationId(UUID.randomUUID())
            .filename("evidence.pdf")
            .documentType("EVIDENCE")
            .uploadedAt(Instant.now())
            .build();
    }
}

// UploadDocumentRequestGenerator.java
public class UploadDocumentRequestGenerator extends BaseGenerator<UploadDocumentRequest, UploadDocumentRequest.Builder> {

    public UploadDocumentRequestGenerator() {
        super(UploadDocumentRequest::toBuilder, UploadDocumentRequest.Builder::build);
    }

    @Override
    public UploadDocumentRequest createDefault() {
        return UploadDocumentRequest.builder()
            .filename("evidence.pdf")
            .documentType("EVIDENCE")
            .build();
    }
}
```

---

## Step 2 — Repository integration test (`src/integrationTest` — `DocumentRepositoryTest`)

Write this before implementing the service. It proves that the entity and migration are correct and that the repository methods work before anything else depends on them.

```java
public class DocumentRepositoryTest extends BaseIntegrationTest {

    @Test
    public void givenSaveOfExpectedDocument_whenGetCalled_expectedAndActualAreEqual() {
        // given
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        DocumentEntity expected = persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(application.getId())
        );
        clearCache();

        // when
        DocumentEntity actual = documentRepository.findById(expected.getId()).orElse(null);

        // then — all fields round-trip correctly
        assertThat(expected)
            .usingRecursiveComparison()
            .ignoringFields("createdAt", "modifiedAt")
            .isEqualTo(actual);
        assertThat(expected.getCreatedAt()).isNotNull();   // DB-generated
        assertThat(expected.getModifiedAt()).isNotNull();  // DB-generated
    }

    @Test
    public void givenMultipleDocumentsForApplication_whenFindAllByApplicationId_thenReturnOnlyMatchingDocuments() {
        // given
        ApplicationEntity applicationOne = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        ApplicationEntity applicationTwo = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        DocumentEntity docOne = persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(applicationOne.getId())
        );
        DocumentEntity docTwo = persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(applicationOne.getId())
        );
        persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(applicationTwo.getId())  // should not appear in results
        );
        clearCache();

        // when
        List<DocumentEntity> actual = documentRepository.findAllByApplicationId(applicationOne.getId());

        // then
        assertThat(actual).hasSize(2);
        assertThat(actual).extracting(DocumentEntity::getId)
            .containsExactlyInAnyOrder(docOne.getId(), docTwo.getId());
    }
}
```

---

## Step 3 — Mapper unit test (`src/test` — `DocumentMapperTest`)

Test each mapping direction with all required and optional fields. Because `uploadedAt` is set by the service (not the request), test that the mapper accepts it as a parameter rather than reading it from the request:

```java
@ExtendWith(MockitoExtension.class)
public class DocumentMapperTest extends BaseMapperTest {

    @InjectMocks
    private DocumentMapperImpl documentMapper;

    @Test
    void givenUploadDocumentRequest_whenToDocumentEntity_thenMapsFieldsCorrectly() {
        UUID applicationId = UUID.randomUUID();
        UploadDocumentRequest request = DataGenerator.createDefault(UploadDocumentRequestGenerator.class, builder ->
            builder.filename("contract.pdf")
                   .documentType("CONTRACT")
        );

        DocumentEntity entity = documentMapper.toDocumentEntity(request, applicationId);

        assertThat(entity.getApplicationId()).isEqualTo(applicationId);
        assertThat(entity.getFilename()).isEqualTo("contract.pdf");
        assertThat(entity.getDocumentType()).isEqualTo("CONTRACT");
        assertThat(entity.getUploadedAt()).isNotNull();
    }

    @Test
    void givenNullUploadDocumentRequest_whenToDocumentEntity_thenReturnsNull() {
        assertThat(documentMapper.toDocumentEntity(null, UUID.randomUUID())).isNull();
    }

    @Test
    void givenDocumentEntity_whenToDocument_thenMapsAllFieldsCorrectly() {
        DocumentEntity entity = DataGenerator.createDefault(DocumentEntityGenerator.class, builder ->
            builder.id(UUID.randomUUID())
                   .applicationId(UUID.randomUUID())
                   .filename("contract.pdf")
                   .documentType("CONTRACT")
        );

        Document document = documentMapper.toDocument(entity);

        assertThat(document.getId()).isEqualTo(entity.getId());
        assertThat(document.getApplicationId()).isEqualTo(entity.getApplicationId());
        assertThat(document.getFilename()).isEqualTo("contract.pdf");
        assertThat(document.getDocumentType()).isEqualTo("CONTRACT");
        assertThat(document.getUploadedAt()).isEqualTo(entity.getUploadedAt());
    }

    @Test
    void givenNullDocumentEntity_whenToDocument_thenReturnsNull() {
        assertThat(documentMapper.toDocument(null)).isNull();
    }
}
```

---

## Step 4 — Service unit test (`src/test`)

One test file per service method. Every test uses `BaseServiceTest` (full Spring context, repositories mocked).

### `UploadDocumentTest.java`

```java
public class UploadDocumentTest extends BaseServiceTest {

    @Autowired
    private DocumentService serviceUnderTest;

    @Test
    public void givenValidRequest_whenUploadDocument_thenDocumentSavedAndIdReturned() {
        UUID applicationId = UUID.randomUUID();
        UUID expectedDocumentId = UUID.randomUUID();

        UploadDocumentRequest request = DataGenerator.createDefault(UploadDocumentRequestGenerator.class);
        DocumentEntity savedEntity = DataGenerator.createDefault(DocumentEntityGenerator.class, builder ->
            builder.id(expectedDocumentId).applicationId(applicationId)
        );

        when(applicationRepository.existsById(applicationId)).thenReturn(true);
        when(documentRepository.save(any())).thenReturn(savedEntity);
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when
        UUID actualId = serviceUnderTest.uploadDocument(applicationId, request);

        // then
        assertEquals(expectedDocumentId, actualId);

        // Assert what was saved matches what was requested
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, times(1)).save(captor.capture());
        DocumentEntity captured = captor.getValue();
        assertThat(captured.getApplicationId()).isEqualTo(applicationId);
        assertThat(captured.getFilename()).isEqualTo(request.getFilename());
        assertThat(captured.getDocumentType()).isEqualTo(request.getDocumentType());
        assertThat(captured.getUploadedAt()).isNotNull();
    }

    @Test
    public void givenApplicationNotFound_whenUploadDocument_thenThrowResourceNotFoundException() {
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.existsById(applicationId)).thenReturn(false);
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> serviceUnderTest.uploadDocument(applicationId,
                DataGenerator.createDefault(UploadDocumentRequestGenerator.class)))
            .withMessageContaining("No application found with id: " + applicationId);

        verify(documentRepository, never()).save(any());
    }

    @Test
    public void givenMissingFilename_whenUploadDocument_thenThrowValidationException() {
        setSecurityContext(TestConstants.Roles.CASEWORKER);
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.existsById(applicationId)).thenReturn(true);

        UploadDocumentRequest request = DataGenerator.createDefault(UploadDocumentRequestGenerator.class, builder ->
            builder.filename(null)
        );

        Throwable thrown = catchThrowable(() -> serviceUnderTest.uploadDocument(applicationId, request));

        assertThat(thrown).isInstanceOf(ValidationException.class)
            .hasMessageContaining("filename must not be null");
        verify(documentRepository, never()).save(any());
    }

    @Test
    public void givenNoRole_whenUploadDocument_thenThrowAuthorizationDeniedException() {
        assertThatExceptionOfType(AuthorizationDeniedException.class)
            .isThrownBy(() -> serviceUnderTest.uploadDocument(UUID.randomUUID(),
                DataGenerator.createDefault(UploadDocumentRequestGenerator.class)))
            .withMessageContaining("Access Denied");

        verify(documentRepository, never()).save(any());
    }
}
```

### `GetDocumentsTest.java`

```java
public class GetDocumentsTest extends BaseServiceTest {

    @Autowired
    private DocumentService serviceUnderTest;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    public void givenApplication_whenGetDocuments_thenReturnMappedDocuments(int count) {
        UUID applicationId = UUID.randomUUID();
        List<DocumentEntity> entities = DataGenerator.createMultipleDefault(DocumentEntityGenerator.class, count);
        when(documentRepository.findAllByApplicationId(applicationId)).thenReturn(entities);
        when(applicationRepository.existsById(applicationId)).thenReturn(true);
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        List<Document> result = serviceUnderTest.getDocumentsForApplication(applicationId);

        verify(documentRepository, times(1)).findAllByApplicationId(applicationId);
        assertThat(result).hasSize(count);
        // Each document maps correctly
        for (int i = 0; i < count; i++) {
            assertThat(result.get(i).getFilename()).isEqualTo(entities.get(i).getFilename());
            assertThat(result.get(i).getDocumentType()).isEqualTo(entities.get(i).getDocumentType());
        }
    }

    @Test
    public void givenApplicationNotFound_whenGetDocuments_thenThrowResourceNotFoundException() {
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.existsById(applicationId)).thenReturn(false);
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> serviceUnderTest.getDocumentsForApplication(applicationId))
            .withMessageContaining("No application found with id: " + applicationId);

        verify(documentRepository, never()).findAllByApplicationId(any());
    }

    @Test
    public void givenNoRole_whenGetDocuments_thenThrowAuthorizationDeniedException() {
        assertThatExceptionOfType(AuthorizationDeniedException.class)
            .isThrownBy(() -> serviceUnderTest.getDocumentsForApplication(UUID.randomUUID()))
            .withMessageContaining("Access Denied");

        verify(documentRepository, never()).findAllByApplicationId(any());
    }
}
```

---

## Step 5 — Controller integration test (`src/integrationTest`)

One test file per endpoint. Each test persists data, makes HTTP calls, and asserts the full response.

### `UploadDocumentTest.java`

```java
public class UploadDocumentTest extends BaseHarnessTest {

    @SmokeTest
    @Test
    public void givenValidRequest_whenUploadDocument_thenReturnCreatedWithLocationHeader() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
            builder -> builder.caseworker(CaseworkerJohnDoe)
        );

        UploadDocumentRequest request = DataGenerator.createDefault(UploadDocumentRequestGenerator.class);

        HarnessResult result = postUri(TestConstants.URIs.UPLOAD_DOCUMENT, request, application.getId());

        assertSecurityHeaders(result);
        assertCreated(result);

        UUID documentId = HeaderUtils.GetUUIDFromLocation(result.getResponse().getHeader("Location"));
        persistedDataGenerator.trackExistingDocument(documentId);

        // Confirm what was persisted
        DocumentEntity saved = documentRepository.findById(documentId).orElseThrow();
        assertThat(saved.getApplicationId()).isEqualTo(application.getId());
        assertThat(saved.getFilename()).isEqualTo(request.getFilename());
        assertThat(saved.getDocumentType()).isEqualTo(request.getDocumentType());
        assertThat(saved.getUploadedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getModifiedAt()).isNotNull();
    }

    @Test
    public void givenApplicationNotFound_whenUploadDocument_thenReturnNotFound() throws Exception {
        UUID nonExistentApplicationId = UUID.randomUUID();
        UploadDocumentRequest request = DataGenerator.createDefault(UploadDocumentRequestGenerator.class);

        HarnessResult result = postUri(TestConstants.URIs.UPLOAD_DOCUMENT, request, nonExistentApplicationId);

        assertSecurityHeaders(result);
        assertNotFound(result);
        ProblemDetail problem = deserialise(result, ProblemDetail.class);
        assertEquals("No application found with id: " + nonExistentApplicationId, problem.getDetail());
    }

    @ParameterizedTest
    @MethodSource("invalidUploadRequests")
    public void givenInvalidRequest_whenUploadDocument_thenReturnBadRequest(
            UploadDocumentRequest request, Map<String, Object> expectedInvalidFields) throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        HarnessResult result = postUri(TestConstants.URIs.UPLOAD_DOCUMENT, request, application.getId());

        assertSecurityHeaders(result);
        assertBadRequest(result);
        ProblemDetail problem = deserialise(result, ProblemDetail.class);
        assertEquals(expectedInvalidFields, problem.getProperties().get("invalidFields"));
    }

    private static Stream<Arguments> invalidUploadRequests() {
        return Stream.of(
            Arguments.of(
                DataGenerator.createDefault(UploadDocumentRequestGenerator.class, b -> b.filename(null)),
                Map.of("filename", "must not be null")
            ),
            Arguments.of(
                DataGenerator.createDefault(UploadDocumentRequestGenerator.class, b -> b.documentType(null)),
                Map.of("documentType", "must not be null")
            ),
            Arguments.of(
                DataGenerator.createDefault(UploadDocumentRequestGenerator.class, b -> b.filename(null).documentType(null)),
                Map.of("filename", "must not be null", "documentType", "must not be null")
            )
        );
    }

    @Test
    public void givenNoAuthentication_whenUploadDocument_thenReturnUnauthorised() throws Exception {
        withNoToken();
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        HarnessResult result = postUri(TestConstants.URIs.UPLOAD_DOCUMENT,
            DataGenerator.createDefault(UploadDocumentRequestGenerator.class), application.getId());
        assertUnauthorised(result);
    }

    @Test
    public void givenUnknownRole_whenUploadDocument_thenReturnForbidden() throws Exception {
        withToken(TestConstants.Tokens.UNKNOWN);
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        HarnessResult result = postUri(TestConstants.URIs.UPLOAD_DOCUMENT,
            DataGenerator.createDefault(UploadDocumentRequestGenerator.class), application.getId());
        assertForbidden(result);
    }
}
```

### `GetDocumentsTest.java`

```java
public class GetDocumentsTest extends BaseHarnessTest {

    @SmokeTest
    @Test
    public void givenDocumentsExistForApplication_whenGetDocuments_thenReturnOkWithAllDocuments() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        DocumentEntity docOne = persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(application.getId()).filename("contract.pdf")
        );
        DocumentEntity docTwo = persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(application.getId()).filename("evidence.pdf")
        );

        HarnessResult result = getUri(TestConstants.URIs.GET_DOCUMENTS, application.getId());
        List<Document> documents = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, Document.class)
        );

        assertSecurityHeaders(result);
        assertOK(result);
        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(Document::getFilename)
            .containsExactlyInAnyOrder("contract.pdf", "evidence.pdf");
        // Assert every field on one document
        Document actual = documents.stream()
            .filter(d -> d.getFilename().equals("contract.pdf")).findFirst().orElseThrow();
        assertEquals(docOne.getId(), actual.getId());
        assertEquals(docOne.getApplicationId(), actual.getApplicationId());
        assertEquals(docOne.getDocumentType(), actual.getDocumentType());
        assertNotNull(actual.getUploadedAt());
    }

    @Test
    public void givenNoDocumentsForApplication_whenGetDocuments_thenReturnOkWithEmptyList() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        HarnessResult result = getUri(TestConstants.URIs.GET_DOCUMENTS, application.getId());
        List<Document> documents = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, Document.class)
        );

        assertOK(result);
        assertThat(documents).isEmpty();
    }

    @Test
    public void givenApplicationNotFound_whenGetDocuments_thenReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        HarnessResult result = getUri(TestConstants.URIs.GET_DOCUMENTS, nonExistentId);
        assertNotFound(result);
    }

    @Test
    public void givenDocumentsForDifferentApplications_whenGetDocuments_thenReturnOnlyMatchingDocuments() throws Exception {
        ApplicationEntity applicationOne = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        ApplicationEntity applicationTwo = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(applicationOne.getId())
        );
        persistedDataGenerator.createAndPersist(DocumentEntityGenerator.class,
            builder -> builder.applicationId(applicationTwo.getId())  // must not appear in result
        );

        HarnessResult result = getUri(TestConstants.URIs.GET_DOCUMENTS, applicationOne.getId());
        List<Document> documents = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, Document.class)
        );

        assertOK(result);
        assertThat(documents).hasSize(1);
        assertEquals(applicationOne.getId(), documents.get(0).getApplicationId());
    }
}
```

---

## Teardown

`PersistedDataGenerator` must track and clean up `DocumentEntity` instances. Add document tracking alongside the existing application tracking so that `@AfterEach` in `BaseHarnessTest` deletes test documents after each test:

```java
// In PersistedDataGenerator
public void trackExistingDocument(UUID documentId) {
    trackedDocumentIds.add(documentId);
}

// In deleteTrackedData()
documentRepository.deleteAllById(trackedDocumentIds);
trackedDocumentIds.clear();
```

---

## What the test suite proves

| Assertion | Where |
|---|---|
| All `DocumentEntity` fields are persisted and retrieved correctly | Repository integration test |
| Custom finder `findAllByApplicationId` returns only matching records | Repository integration test |
| Mapper correctly translates both directions for all fields | Mapper unit test |
| `uploadDocument` saves the correct entity and returns the generated ID | Service unit test (ArgumentCaptor) |
| `getDocumentsForApplication` returns all mapped documents | Service unit test |
| All validation errors produce the correct exception | Service unit test |
| All auth failures are rejected before any persistence | Service unit test |
| `POST /applications/{id}/documents` → 201 + correct `Location` + entity persisted | Controller integration test |
| `GET /applications/{id}/documents` → 200 + every response field correct | Controller integration test |
| Isolation: documents for other applications are not returned | Controller integration test |
| HTTP validation errors → 400 with correct `invalidFields` | Controller integration test |
| 401 and 403 responses | Controller integration test |

