package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UploadApplicationDocumentResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import util.ProjectionAwaiter;

/** Full HTTP/Postgres/Axon integration tests for the Application draft/submit lifecycle. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationDraftIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private QueryGateway queryGateway;

  @MockitoBean private SdsService sdsService;

  private ProjectionAwaiter projectionAwaiter;

  @PostConstruct
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenValidContent_whenSaveApplicationDraft_thenPersistsDraftAndReturnsCreated() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SaveApplicationDraftResponse body =
        objectMapper.readValue(response.getBody(), SaveApplicationDraftResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applicationId);
    assertThat(body.getSavedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            applicationUrl(applicationId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenDuplicateId_whenSaveApplicationDraft_thenReturnsConflict() {
    UUID applicationId = saveValidDraft();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-999")
            .applicationContent(validApplicationContent(applicationId, UUID.randomUUID()))
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenIdenticalRetry_whenSaveApplicationDraft_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(validApplicationContent(applicationId, UUID.randomUUID()))
            .build();

    ResponseEntity<String> first =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> retry =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SaveApplicationDraftResponse body =
        objectMapper.readValue(retry.getBody(), SaveApplicationDraftResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applicationId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
  }

  @Test
  void givenMissingId_whenSaveApplicationDraft_thenReturnsBadRequest() {
    SaveApplicationDraftRequest request =
        SaveApplicationDraftRequest.builder()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(Map.of("key", "value"))
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void givenNullStatus_whenSaveApplicationDraft_thenReturnsBadRequestAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .laaReference("LAA-123")
            .applicationContent(Map.of("key", "value"))
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void givenBlankLaaReference_whenSaveApplicationDraft_thenReturnsBadRequestAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("   ")
            .applicationContent(Map.of("key", "value"))
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void
      givenEmptyApplicationContent_whenSaveApplicationDraft_thenReturnsBadRequestAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(Map.of())
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void
      givenSchemaInvalidContent_whenSaveApplicationDraft_thenReturnsBadRequestAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content =
        new HashMap<>(validApplicationContent(applicationId, UUID.randomUUID()));
    content.remove("submittedAt");
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void givenContentWithoutLeadProceeding_whenSaveApplicationDraft_thenReturnsBadRequest() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content =
        new HashMap<>(validApplicationContent(applicationId, UUID.randomUUID()));
    Map<String, Object> proceeding = firstProceeding(content);
    proceeding.put("leadProceeding", false);
    content.put("proceedings", List.of(proceeding));
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("No lead proceeding found in application content");
  }

  @Test
  void givenUnparseableSubmissionTimestamp_whenSaveApplicationDraft_thenReturnsBadRequest() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content =
        new HashMap<>(validApplicationContent(applicationId, UUID.randomUUID()));
    content.put("submittedAt", "not-an-instant");
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("submittedAt")
        .contains("must be a valid RFC 3339 date-time");
  }

  @Test
  void givenInvalidNumericFormat_whenSaveApplicationDraft_thenReturnsBadRequest() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content =
        new HashMap<>(validApplicationContent(applicationId, UUID.randomUUID()));
    Map<String, Object> proceeding = firstProceeding(content);
    proceeding.put("substantiveCostLimitation", "not-a-number");
    content.put("proceedings", List.of(proceeding));
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("substantiveCostLimitation");
  }

  @Test
  void givenNullLaaReference_whenSaveApplicationDraft_thenReturnsBadRequest() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .applicationContent(validApplicationContent(applicationId, UUID.randomUUID()))
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void
      givenConcurrentIdenticalRequests_whenSaveApplicationDraftPosted_thenBothSucceedWithOnePersistedDraft()
          throws Exception {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();
    HttpEntity<CreateApplicationDraftRequest> entity = new HttpEntity<>(request, headers());

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<String>> f1 =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  barrier.await(10, TimeUnit.SECONDS);
                  return restTemplate.postForEntity(saveDraftUrl(), entity, String.class);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              executor);
      CompletableFuture<ResponseEntity<String>> f2 =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  barrier.await(10, TimeUnit.SECONDS);
                  return restTemplate.postForEntity(saveDraftUrl(), entity, String.class);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              executor);

      ResponseEntity<String> r1 = f1.get(20, TimeUnit.SECONDS);
      ResponseEntity<String> r2 = f2.get(20, TimeUnit.SECONDS);

      // Both requests must resolve successfully regardless of which wins the concurrency race.
      assertThat(r1.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(r2.getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      executor.shutdown();
    }

    // The draft store must contain exactly one row for this application ID.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
  }

  @Test
  void givenValidDraft_whenSubmitApplicationDraft_thenCreatesApplicationAndDeletesDraft() {
    UUID applicationId = saveValidDraft();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    SubmitApplicationDraftResponse body =
        objectMapper.readValue(response.getBody(), SubmitApplicationDraftResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applicationId);
    assertThat(body.getSubmittedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_data"
                    + " WHERE application_id = ? AND version = 0",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    var application = projectionAwaiter.awaitApplication(applicationId);
    assertThat(application.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(application.getLaaReference()).isEqualTo("LAA-123");

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            applicationUrl(applicationId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse projectedApplication =
        objectMapper.readValue(getResponse.getBody(), ApplicationResponse.class);
    assertThat(projectedApplication.getApplicationId()).isEqualTo(applicationId);
    assertThat(projectedApplication.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(projectedApplication.getLaaReference()).isEqualTo("LAA-123");
  }

  @Test
  void givenNoDraft_whenSubmitApplicationDraft_thenReturnsNotFound() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void
      givenDraftWithPotentialDuplicates_whenSubmitApplicationDraft_thenPotentialDuplicatesArePersistedAndProjected() {
    UUID applicationId = UUID.randomUUID();
    UUID duplicateId1 = UUID.randomUUID();
    UUID duplicateId2 = UUID.randomUUID();
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    List<PotentialDuplicate> potentialDuplicates =
        List.of(
            PotentialDuplicate.builder()
                .applicationId(duplicateId1)
                .laaReference("LAA-00001")
                .legacyReference("LEGACY-001")
                .build(),
            PotentialDuplicate.builder()
                .applicationId(duplicateId2)
                .laaReference("LAA-00002")
                .legacyReference(null)
                .build());
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .potentialDuplicates(potentialDuplicates)
            .build();
    ResponseEntity<String> saveResponse =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);

    var projected = projectionAwaiter.awaitApplication(applicationId);
    assertThat(projected.getPotentialDuplicates()).hasSize(2);
    assertThat(projected.getPotentialDuplicates().get(0).getApplicationId())
        .isEqualTo(duplicateId1);
    assertThat(projected.getPotentialDuplicates().get(0).getLaaReference()).isEqualTo("LAA-00001");
    assertThat(projected.getPotentialDuplicates().get(0).getLegacyReference())
        .isEqualTo("LEGACY-001");
    assertThat(projected.getPotentialDuplicates().get(1).getApplicationId())
        .isEqualTo(duplicateId2);
    assertThat(projected.getPotentialDuplicates().get(1).getLaaReference()).isEqualTo("LAA-00002");
    assertThat(projected.getPotentialDuplicates().get(1).getLegacyReference()).isNull();

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            applicationUrl(applicationId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse projectedApplication =
        objectMapper.readValue(getResponse.getBody(), ApplicationResponse.class);
    assertThat(projectedApplication.getPotentialDuplicates()).hasSize(2);
    assertThat(projectedApplication.getPotentialDuplicates().get(0).getApplicationId())
        .isEqualTo(duplicateId1);
    assertThat(projectedApplication.getPotentialDuplicates().get(0).getLaaReference())
        .isEqualTo("LAA-00001");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenUploadedDocument_whenDownloaded_thenStreamsContentWithOriginalFilename(
      boolean submitted) {
    UUID applicationId = saveValidDraft();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(applicationId, "original evidence.pdf");
    if (submitted) {
      ResponseEntity<String> submitResponse =
          restTemplate.postForEntity(
              submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);
      assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    }
    byte[] content = "%PDF-1.4\ncontent".getBytes();
    when(sdsService.getEvidenceFile(applicationId, documentId, "original evidence.pdf"))
        .thenReturn(new ByteArrayResource(content));

    ResponseEntity<byte[]> response =
        await()
            .atMost(Duration.ofSeconds(10))
            .until(
                () ->
                    restTemplate.exchange(
                        documentUrl(applicationId, documentId),
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        byte[].class),
                candidate -> candidate.getStatusCode() == HttpStatus.OK);

    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo("original evidence.pdf");
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(response.getBody()).containsExactly(content);
  }

  @Test
  void givenUnknownDocument_whenDownloaded_thenReturnsNotFoundWithoutCallingSds() {
    UUID applicationId = saveValidDraft();

    ResponseEntity<String> response =
        restTemplate.exchange(
            documentUrl(applicationId, UUID.randomUUID()),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(sdsService, never()).getEvidenceFile(any(), any(), any());
  }

  private UUID uploadDocument(UUID applicationId, String filename) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        new ByteArrayResource("%PDF-1.4\ncontent".getBytes()) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    body.add("documentType", "GATEWAY_EVIDENCE");
    HttpHeaders multipartHeaders = headers();
    multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            applicationUrl(applicationId) + "/documents",
            new HttpEntity<>(body, multipartHeaders),
            String.class);
    assertThat(response.getStatusCode())
        .withFailMessage("Upload response: %s", response.getBody())
        .isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), UploadApplicationDocumentResponse.class)
        .getDocumentId();
  }

  private String documentUrl(UUID applicationId, UUID documentId) {
    return applicationUrl(applicationId) + "/documents/" + documentId;
  }

  private UUID saveDraft(UUID applicationId, String laaReference, Map<String, Object> content) {
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference(laaReference)
            .applicationContent(content)
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), SaveApplicationDraftResponse.class)
        .getApplicationId();
  }

  private UUID saveValidDraft() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    return saveDraft(applicationId, "LAA-123", content);
  }

  private String saveDraftUrl() {
    return "http://localhost:" + port + "/api/v0/application-drafts";
  }

  private String draftUrl(UUID applicationId) {
    return saveDraftUrl() + "/" + applicationId;
  }

  private String submitUrl(UUID applicationId) {
    return draftUrl(applicationId) + "/submit";
  }

  private String applicationUrl(UUID applicationId) {
    return "http://localhost:" + port + "/api/v0/applications/" + applicationId;
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }

  private Map<String, Object> firstProceeding(Map<String, Object> applicationContent) {
    Map<?, ?> source = (Map<?, ?>) ((List<?>) applicationContent.get("proceedings")).getFirst();
    Map<String, Object> proceeding = new HashMap<>();
    source.forEach((key, value) -> proceeding.put(key.toString(), value));
    return proceeding;
  }
}
