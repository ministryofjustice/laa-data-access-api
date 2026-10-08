package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UploadApplicationDocumentResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjection;
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

  @MockitoSpyBean private ApplicationDraftStore draftStore;

  @MockitoSpyBean private ApplicationDataStore applicationDataStore;

  @MockitoSpyBean private ApplicationProjection applicationProjection;

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

  @Test
  void givenConcurrentUploads_whenRegistered_thenBothDocumentsAndFilenamesSurvive()
      throws Exception {
    UUID applicationId = saveValidDraft();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    CyclicBarrier staleReads = new CyclicBarrier(2);
    AtomicInteger writeAttempts = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (writeAttempts.incrementAndGet() <= 2) {
                staleReads.await(10, TimeUnit.SECONDS);
              }
              return invocation.callRealMethod();
            })
        .when(draftStore)
        .upsert(any(), any(), any(), any());
    int eventsBefore = eventCount(applicationId);

    List<ResponseEntity<String>> responses =
        race(
            () ->
                restTemplate.postForEntity(
                    applicationUrl(applicationId) + "/documents",
                    uploadRequest("first.pdf"),
                    String.class),
            () ->
                restTemplate.postForEntity(
                    applicationUrl(applicationId) + "/documents",
                    uploadRequest("second.PDF"),
                    String.class));

    assertThat(responses)
        .allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED));
    List<UUID> documentIds =
        responses.stream()
            .map(
                response ->
                    objectMapper
                        .readValue(response.getBody(), UploadApplicationDocumentResponse.class)
                        .getDocumentId())
            .toList();
    assertThat(documentIds).doesNotHaveDuplicates();
    assertThat(eventCount(applicationId)).isEqualTo(eventsBefore + 2);
    assertThat(writeAttempts.get()).isEqualTo(3);
    for (int index = 0; index < documentIds.size(); index++) {
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT payload -> 'documentFilenames' ->> ? FROM axon.application_draft WHERE application_id = ?",
                  String.class,
                  documentIds.get(index).toString(),
                  applicationId))
          .isEqualTo(index == 0 ? "first.pdf" : "second.PDF");
      byte[] content = "%PDF-1.4\ncontent".getBytes();
      when(sdsService.getEvidenceFile(
              applicationId,
              documentIds.get(index),
              documentIds.get(index) + (index == 0 ? ".pdf" : ".PDF")))
          .thenReturn(new ByteArrayResource(content));
      UUID documentId = documentIds.get(index);
      String filename = index == 0 ? "first.pdf" : "second.PDF";
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                ResponseEntity<byte[]> response =
                    restTemplate.exchange(
                        documentUrl(applicationId, documentId),
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        byte[].class);
                assertThat(response.getStatusCode())
                    .withFailMessage("Download response: %s", new String(response.getBody()))
                    .isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo(filename);
                assertThat(response.getBody()).containsExactly(content);
              });
    }
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void givenUploadOverlapsSubmission_whenEitherCommitsFirst_thenSubmittedContentIsConsistent(
      boolean uploadCommitsFirst) throws Exception {
    UUID applicationId = saveValidDraft();
    AtomicReference<UUID> documentId = new AtomicReference<>();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              documentId.set(invocation.getArgument(1));
              return new SdsUploadResult(null, null, "checksum");
            });
    CountDownLatch staleRead = new CountDownLatch(1);
    CountDownLatch releaseWrite = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    org.mockito.stubbing.Answer<Object> gate =
        invocation -> {
          if (attempts.incrementAndGet() == 1) {
            staleRead.countDown();
            assertThat(releaseWrite.await(20, TimeUnit.SECONDS)).isTrue();
          }
          return invocation.callRealMethod();
        };
    if (uploadCommitsFirst) {
      doAnswer(gate)
          .when(applicationDataStore)
          .append(eq(applicationId), anyLong(), any(), any(), any());
    } else {
      doAnswer(gate).when(draftStore).upsert(eq(applicationId), any(), any(), any());
    }
    int eventsBefore = eventCount(applicationId);
    Supplier<ResponseEntity<String>> upload =
        () ->
            restTemplate.postForEntity(
                applicationUrl(applicationId) + "/documents",
                uploadRequest("overlap.PDF"),
                String.class);
    Supplier<ResponseEntity<String>> submit =
        () ->
            restTemplate.postForEntity(
                submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<ResponseEntity<String>> losingRequest =
          CompletableFuture.supplyAsync(uploadCommitsFirst ? submit : upload, executor);
      assertThat(staleRead.await(10, TimeUnit.SECONDS)).isTrue();
      ResponseEntity<String> winningResponse = (uploadCommitsFirst ? upload : submit).get();
      assertThat(winningResponse.getStatusCode())
          .withFailMessage("Winning response: %s", winningResponse.getBody())
          .isIn(HttpStatus.CREATED, HttpStatus.OK, HttpStatus.ACCEPTED);
      releaseWrite.countDown();
      ResponseEntity<String> losingResponse = losingRequest.get(30, TimeUnit.SECONDS);
      if (uploadCommitsFirst) {
        assertThat(losingResponse.getStatusCode())
            .withFailMessage("Submission response: %s", losingResponse.getBody())
            .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
        assertThat(attempts.get()).isEqualTo(2);
      } else {
        assertThat(losingResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      }
      assertThat(eventCount(applicationId)).isEqualTo(eventsBefore + (uploadCommitsFirst ? 2 : 1));
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                  Integer.class,
                  applicationId))
          .isZero();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ?",
                  Integer.class,
                  applicationId))
          .isEqualTo(1);
      var filenames = applicationDataStore.get(applicationId, 0L).documentFilenames();
      if (uploadCommitsFirst) {
        assertThat(filenames).containsExactly(Map.entry(documentId.get(), "overlap.PDF"));
        assertThat(projectionAwaiter.awaitApplication(applicationId).getUploadedDocuments())
            .singleElement()
            .satisfies(document -> assertThat(document.documentId()).isEqualTo(documentId.get()));
      } else {
        assertThat(filenames).isEmpty();
        assertThat(projectionAwaiter.awaitApplication(applicationId).getUploadedDocuments())
            .isNullOrEmpty();
      }
      verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
    } finally {
      releaseWrite.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void givenUploadPausedInSds_whenSubmissionCommits_thenLateRegistrationIsRejected()
      throws Exception {
    UUID applicationId = saveValidDraft();
    CountDownLatch storedFile = new CountDownLatch(1);
    CountDownLatch releaseUpload = new CountDownLatch(1);
    AtomicReference<UUID> documentId = new AtomicReference<>();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              documentId.set(invocation.getArgument(1));
              storedFile.countDown();
              assertThat(releaseUpload.await(20, TimeUnit.SECONDS)).isTrue();
              return new SdsUploadResult(null, null, "checksum");
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<ResponseEntity<String>> upload =
          CompletableFuture.supplyAsync(
              () ->
                  restTemplate.postForEntity(
                      applicationUrl(applicationId) + "/documents",
                      uploadRequest("late.PDF"),
                      String.class),
              executor);
      assertThat(storedFile.await(10, TimeUnit.SECONDS)).isTrue();
      ResponseEntity<String> submitted =
          restTemplate.postForEntity(
              submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);
      assertThat(submitted.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
      final int eventsAfterSubmission = eventCount(applicationId);
      releaseUpload.countDown();
      ResponseEntity<String> response = upload.get(30, TimeUnit.SECONDS);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("application draft");
      assertThat(eventCount(applicationId)).isEqualTo(eventsAfterSubmission);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                  Integer.class,
                  applicationId))
          .isZero();
      assertThat(applicationDataStore.get(applicationId, 0L).documentFilenames()).isEmpty();
      assertThat(projectionAwaiter.awaitApplication(applicationId).getUploadedDocuments())
          .isNullOrEmpty();
      assertThat(documentId.get()).isNotNull();
      verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
    } finally {
      releaseUpload.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void givenUploadProjectionPaused_whenDownloaded_thenNotFoundUntilMetadataIsProjected()
      throws Exception {
    UUID applicationId = saveValidDraft();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    CountDownLatch projectionStarted = new CountDownLatch(1);
    CountDownLatch releaseProjection = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              ApplicationDocumentUploadedEvent event = invocation.getArgument(0);
              if (event.applicationId().equals(applicationId)) {
                projectionStarted.countDown();
                assertThat(releaseProjection.await(20, TimeUnit.SECONDS)).isTrue();
              }
              return invocation.callRealMethod();
            })
        .when(applicationProjection)
        .on(any(ApplicationDocumentUploadedEvent.class));
    try {
      UUID documentId = uploadDocument(applicationId, "projected.PDF");
      assertThat(projectionStarted.await(10, TimeUnit.SECONDS)).isTrue();
      byte[] content = "%PDF-1.4\ncontent".getBytes();
      when(sdsService.getEvidenceFile(applicationId, documentId, documentId + ".PDF"))
          .thenReturn(new ByteArrayResource(content));

      ResponseEntity<byte[]> pending =
          restTemplate.exchange(
              documentUrl(applicationId, documentId),
              HttpMethod.GET,
              new HttpEntity<>(headers()),
              byte[].class);
      assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      verify(sdsService, never()).getEvidenceFile(any(), any(), any());

      releaseProjection.countDown();
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                ResponseEntity<byte[]> projected =
                    restTemplate.exchange(
                        documentUrl(applicationId, documentId),
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        byte[].class);
                assertThat(projected.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(projected.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo("projected.PDF");
                assertThat(projected.getBody()).containsExactly(content);
              });
      verify(sdsService).getEvidenceFile(applicationId, documentId, documentId + ".PDF");
    } finally {
      releaseProjection.countDown();
    }
  }

  @Test
  void givenUploadConcurrencyRetriesExhausted_whenPosted_thenReturnsServerErrorAndRollsBack() {
    UUID applicationId = saveValidDraft();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    AtomicInteger registrationAttempts = new AtomicInteger();
    doAnswer(
            invocation -> {
              registrationAttempts.incrementAndGet();
              return invocation.callRealMethod();
            })
        .when(draftStore)
        .upsert(eq(applicationId), any(), any(), any());
    int eventsBefore = eventCount(applicationId);
    jdbcTemplate.execute(
        """
                CREATE OR REPLACE FUNCTION axon.reject_test_application_document()
                RETURNS trigger AS $$
                BEGIN
                        IF NEW.aggregate_identifier = '%s'
                                AND NEW.payload_type LIKE '%%ApplicationDocumentUploadedEvent' THEN
                                RAISE EXCEPTION 'forced application upload concurrency failure'
                                        USING ERRCODE = '23505';
                        END IF;
                        RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """
            .formatted(applicationId));
    jdbcTemplate.execute(
        """
                CREATE TRIGGER reject_test_application_document
                BEFORE INSERT ON axon.domain_event_entry
                FOR EACH ROW EXECUTE FUNCTION axon.reject_test_application_document()
                """);
    try {
      ResponseEntity<String> response =
          restTemplate.postForEntity(
              applicationUrl(applicationId) + "/documents",
              uploadRequest("retry.pdf"),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(registrationAttempts.get()).isEqualTo(2);
      assertThat(eventCount(applicationId)).isEqualTo(eventsBefore);
      assertThat(draftStore.find(applicationId).orElseThrow().documentFilenames()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ?",
                  Integer.class,
                  applicationId))
          .isZero();
      verify(sdsService).saveEvidenceFile(eq(applicationId), any(), any());
      verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_application_document ON axon.domain_event_entry");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_application_document()");
    }
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "false,false,false",
    "false,true,false",
    "true,false,false",
    "true,true,false",
    "true,false,true",
    "true,true,true"
  })
  void givenUploadedDocument_whenDownloaded_thenStreamsContentWithOriginalFilename(
      boolean submitted, boolean missingFilename, boolean decided) {
    UUID applicationId = saveValidDraft();
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    final UUID documentId = uploadDocument(applicationId, "original evidence.pdf");
    if (missingFilename) {
      jdbcTemplate.update(
          "UPDATE axon.application_draft SET payload = jsonb_set(payload, '{documentFilenames}', '{}'::jsonb)"
              + " WHERE application_id = ?",
          applicationId);
    }
    if (submitted) {
      ResponseEntity<String> submitResponse =
          restTemplate.postForEntity(
              submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);
      assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    }
    if (decided) {
      refuseApplication(applicationId);
    }
    byte[] content = "%PDF-1.4\ncontent".getBytes();
    when(sdsService.getEvidenceFile(applicationId, documentId, documentId + ".pdf"))
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
        .isEqualTo(missingFilename ? documentId.toString() : "original evidence.pdf");
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(response.getBody()).containsExactly(content);
    verify(sdsService).getEvidenceFile(applicationId, documentId, documentId + ".pdf");
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
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            applicationUrl(applicationId) + "/documents", uploadRequest(filename), String.class);
    assertThat(response.getStatusCode())
        .withFailMessage("Upload response: %s", response.getBody())
        .isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), UploadApplicationDocumentResponse.class)
        .getDocumentId();
  }

  private HttpEntity<MultiValueMap<String, Object>> uploadRequest(String filename) {
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

    return new HttpEntity<>(body, multipartHeaders);
  }

  private int eventCount(UUID applicationId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
        Integer.class,
        applicationId.toString());
  }

  private List<ResponseEntity<String>> race(
      Supplier<ResponseEntity<String>> first, Supplier<ResponseEntity<String>> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<String>> firstResult =
          CompletableFuture.supplyAsync(first, executor);
      CompletableFuture<ResponseEntity<String>> secondResult =
          CompletableFuture.supplyAsync(second, executor);
      return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private String documentUrl(UUID applicationId, UUID documentId) {
    return applicationUrl(applicationId) + "/documents/" + documentId;
  }

  private void refuseApplication(UUID applicationId) {
    ResponseEntity<Void> ready =
        restTemplate.exchange(
            applicationUrl(applicationId) + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var application = projectionAwaiter.awaitApplicationVersion(applicationId, 1L);
    ResponseEntity<Void> assigned =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/work-list/" + applicationId + "/assign",
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
            Void.class);
    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    MakeDecisionRequest decision =
        MakeDecisionRequest.builder()
            .applicationVersion(1L)
            .overallDecision(DecisionStatus.REFUSED)
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(application.getProceedings().getFirst().getId())
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.REFUSED)
                                .reason("Insufficient evidence")
                                .justification("The evidence did not meet the test")
                                .build())
                        .build()))
            .build();
    ResponseEntity<Void> refused =
        restTemplate.exchange(
            applicationUrl(applicationId) + "/decision",
            HttpMethod.PATCH,
            new HttpEntity<>(decision, headers()),
            Void.class);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var decided = projectionAwaiter.awaitApplicationVersion(applicationId, 2L);
    assertThat(decided.getStatus()).isEqualTo("APPLICATION_REFUSED");
    assertThat(decided.getApplicationDataVersion()).isEqualTo(2L);
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
