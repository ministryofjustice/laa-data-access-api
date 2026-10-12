package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.DocumentType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import util.ProjectionAwaiter;

/** Full HTTP/Postgres/Axon integration tests for the Prior Authority draft/submit lifecycle. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriorAuthorityDraftIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private QueryGateway queryGateway;

  @MockitoBean private SdsService sdsService;

  @MockitoSpyBean private PriorAuthorityDraftStore draftStore;

  private ProjectionAwaiter projectionAwaiter;

  @PostConstruct
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenGrantedApplication_whenSavePriorAuthorityDraft_thenPersistsDraftAndProjects() {
    UUID applicationId = grantedApplication();
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    SavePriorAuthorityDraftResponse body =
        objectMapper.readValue(response.getBody(), SavePriorAuthorityDraftResponse.class);
    UUID priorAuthorityId = body.getPriorAuthorityId();
    assertThat(priorAuthorityId).isNotNull();
    assertThat(body.getSavedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT application_id FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                UUID.class,
                priorAuthorityId))
        .isEqualTo(applicationId);
    projectionAwaiter.awaitPriorAuthority(priorAuthorityId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);

    ResponseEntity<String> draftResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PriorAuthorityResponse draft =
        objectMapper.readValue(draftResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(draft.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(draft.getApplicationId()).isEqualTo(applicationId);
    assertThat(draft.getStatus()).isEqualTo(PriorAuthorityResponse.StatusEnum.DRAFT);
    assertThat(draft.getSubmittedAt()).isNull();
    assertThat(draft.getPriorAuthorityType())
        .isEqualTo(PriorAuthorityResponse.PriorAuthorityTypeEnum.EXPERT);
  }

  @Test
  void givenMissingApplication_whenSavePriorAuthorityDraft_thenReturnsNotFound() {
    UUID nonexistentApplicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(nonexistentApplicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE application_id = ?",
                Integer.class,
                nonexistentApplicationId))
        .isZero();
  }

  @Test
  void givenUngrantedApplication_whenSavePriorAuthorityDraft_thenReturnsBadRequest() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, UUID.randomUUID());
    projectionAwaiter.awaitApplication(applicationId);
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("GRANTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void givenExistingDraft_whenUpdatePriorAuthorityDraft_thenReturns204AndPersistsUpdatedContent() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);

    SavePriorAuthorityDraftRequest updateRequest =
        SavePriorAuthorityDraftRequest.builder().justification("Updated justification").build();
    ResponseEntity<Void> updateResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, headers()),
            Void.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<String> draftResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    PriorAuthorityResponse draft =
        objectMapper.readValue(draftResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(draft.getJustification()).isEqualTo("Updated justification");
  }

  @Test
  void givenDraftPayloadWithNullNestedFields_whenSavePriorAuthorityDraft_thenAcceptsDraft() {
    UUID applicationId = grantedApplication();
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .disbursementDetails(DisbursementDetails.builder().build())
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    SavePriorAuthorityDraftResponse body =
        objectMapper.readValue(response.getBody(), SavePriorAuthorityDraftResponse.class);
    assertThat(body.getPriorAuthorityId()).isNotNull();
  }

  @Test
  void givenDraft_whenSubmitPriorAuthorityDraft_thenTransitionsToSubmittedAndDeletesDraft() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    SubmitPriorAuthorityDraftResponse body =
        objectMapper.readValue(response.getBody(), SubmitPriorAuthorityDraftResponse.class);
    assertThat(body.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(body.getSubmittedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                String.class,
                priorAuthorityId))
        .isEqualTo("SUBMITTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data"
                    + " WHERE prior_authority_id = ? AND data_version = 0",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isZero();

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PriorAuthorityResponse priorAuthority =
        objectMapper.readValue(getResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(priorAuthority.getStatus()).isEqualTo(PriorAuthorityResponse.StatusEnum.SUBMITTED);
    assertThat(priorAuthority.getSubmittedAt()).isEqualTo(body.getSubmittedAt());
    assertThat(priorAuthority.getDisbursementDetails().getDisbursementPurpose())
        .isEqualTo("Court interpreter");
  }

  @Test
  void givenDraftWithUploadedDocument_whenSubmitPriorAuthorityDraft_thenPreservesDocument() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));

    ResponseEntity<String> uploadResponse =
        restTemplate.postForEntity(
            uploadUrl(priorAuthorityId), uploadRequest("evidence.pdf"), String.class);
    assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID documentId =
        objectMapper
            .readValue(uploadResponse.getBody(), UploadPriorAuthorityDocumentResponse.class)
            .getDocumentId();

    ResponseEntity<Void> updateDocumentTypeResponse =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE),
                headers()),
            Void.class);
    assertThat(updateDocumentTypeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments())
                    .singleElement()
                    .satisfies(
                        document -> {
                          assertThat(document.getDocumentId()).isEqualTo(documentId);
                          assertThat(document.getDocumentType())
                              .isEqualTo(DocumentType.GATEWAY_EVIDENCE);
                          assertThat(document.getFileName()).isEqualTo("evidence.pdf");
                        }));

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode())
        .withFailMessage("Submit response: %s", submitResponse.getBody())
        .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);

    PriorAuthorityResponse priorAuthority = awaitSubmittedPriorAuthority(priorAuthorityId);
    assertThat(priorAuthority.getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.getDocumentId()).isEqualTo(documentId);
              assertThat(document.getDocumentType()).isEqualTo(DocumentType.GATEWAY_EVIDENCE);
              assertThat(document.getFileName()).isEqualTo("evidence.pdf");
            });
  }

  @Test
  void givenUnknownDocumentType_whenUpdatedOverHttp_thenRejectsWithoutAppendingEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("documentType", "UNKNOWN_TYPE"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
                Integer.class,
                priorAuthorityId.toString()))
        .isZero();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "false,false",
    "false,true",
    "true,false",
    "true,true"
  })
  void givenUploadedDocument_whenDownloaded_thenStreamsContentWithOriginalFilename(
      boolean submitted, boolean missingFilename) {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    awaitDocument(priorAuthorityId, documentId);
    updateDocumentType(priorAuthorityId, documentId);
    if (missingFilename) {
      jdbcTemplate.update(
          "UPDATE axon.prior_authority_draft SET payload = jsonb_set(payload, '{documentFilenames}', '{}'::jsonb) WHERE prior_authority_id = ?",
          priorAuthorityId);
    }
    if (submitted) {
      submitDraft(priorAuthorityId);
      awaitSubmittedPriorAuthority(priorAuthorityId);
    }
    byte[] content = "%PDF-1.4\ncontent".getBytes();
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, documentId + ".pdf"))
        .thenReturn(new ByteArrayResource(content));

    ResponseEntity<byte[]> response =
        restTemplate.exchange(
            documentContentUrl(priorAuthorityId, documentId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            byte[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
    assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo(missingFilename ? documentId.toString() : "evidence.pdf");
    assertThat(response.getHeaders().getFirst("X-Document-Uploaded-At")).isNotBlank();
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(response.getBody()).containsExactly(content);
    verify(sdsService).getEvidenceFile(priorAuthorityId, documentId, documentId + ".pdf");
  }

  @Test
  void givenUnknownDocument_whenDownloaded_thenReturnsNotFoundWithoutCallingSds() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);

    ResponseEntity<String> response =
        restTemplate.exchange(
            documentContentUrl(priorAuthorityId, UUID.randomUUID()),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(sdsService, never()).getEvidenceFile(any(), any(), any());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"evidence.pdf", "evidence.PDF", "evidence"})
  void
      givenDraftWithUploadedDocument_whenDeletePriorAuthorityDocument_thenReturnsNoContentAndRemovesDocument(
          String filename) {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, filename);
    awaitDocument(priorAuthorityId, documentId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload -> 'documentFilenames' ->> ? FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                String.class,
                documentId.toString(),
                priorAuthorityId))
        .isEqualTo(filename);

    String suffix =
        filename.substring(
            filename.lastIndexOf('.') < 0 ? filename.length() : filename.lastIndexOf('.'));
    String eventPayload =
        jdbcTemplate.queryForObject(
            "SELECT convert_from(payload, 'UTF8') FROM axon.domain_event_entry WHERE aggregate_identifier = ? AND payload_type LIKE '%PriorAuthorityDocumentUploadedEvent'",
            String.class, priorAuthorityId.toString());
    assertThat(objectMapper.readTree(eventPayload).get("fileSuffix").asString()).isEqualTo(suffix);
    assertThat(eventPayload).doesNotContain(filename);
    jdbcTemplate.update(
        "UPDATE axon.prior_authority_draft SET payload = jsonb_set(payload, '{documentFilenames}', '{}'::jsonb) WHERE prior_authority_id = ?",
        priorAuthorityId);

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange(
            deleteDocumentUrl(priorAuthorityId, documentId),
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            Void.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(sdsService).deleteEvidenceFile(priorAuthorityId, documentId, documentId + suffix);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              ResponseEntity<String> draftResponse =
                  restTemplate.exchange(
                      priorAuthorityUrl(priorAuthorityId),
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      String.class);
              assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
              PriorAuthorityResponse draft =
                  objectMapper.readValue(draftResponse.getBody(), PriorAuthorityResponse.class);
              assertThat(draft.getUploadedDocuments()).isEmpty();
            });

    ResponseEntity<String> downloadResponse =
        restTemplate.exchange(
            documentContentUrl(priorAuthorityId, documentId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(sdsService, never()).getEvidenceFile(any(), any(), any());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "PATCH", "DELETE"})
  void givenDocumentOwnedByAnotherPriorAuthority_whenAccessed_thenRejectsWithoutSdsOrEvents(
      String method) {
    UUID applicationId = grantedApplication();
    UUID ownerId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);
    UUID otherId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(ownerId, "evidence.pdf");
    awaitDocument(ownerId, documentId);
    int eventsBefore = eventCount(otherId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            documentUrl(otherId, documentId),
            HttpMethod.valueOf(method),
            new HttpEntity<>(
                "PATCH".equals(method)
                    ? new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE)
                    : null,
                headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(eventCount(otherId)).isEqualTo(eventsBefore);
    assertThat(getPriorAuthority(ownerId).getUploadedDocuments())
        .singleElement()
        .satisfies(document -> assertThat(document.getDocumentId()).isEqualTo(documentId));
    verify(sdsService, never()).getEvidenceFile(any(), any(), any());
    verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
  }

  @Test
  void givenSdsCleanupFails_whenDeleted_thenLogicalDeletionStillCommits() {
    UUID priorAuthorityId = saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.PDF");
    awaitDocument(priorAuthorityId, documentId);
    doThrow(new IllegalStateException("SDS unavailable"))
        .when(sdsService)
        .deleteEvidenceFile(priorAuthorityId, documentId, documentId + ".PDF");
    int eventsBefore = eventCount(priorAuthorityId);

    ResponseEntity<Void> response = deleteDocument(priorAuthorityId, documentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsBefore + 1);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments()).isEmpty());
    assertThat(
            restTemplate
                .exchange(
                    documentUrl(priorAuthorityId, documentId),
                    HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(sdsService, never()).getEvidenceFile(any(), any(), any());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "POST,PriorAuthorityDocumentUploadedEvent",
    "PATCH,PriorAuthorityDocumentTypeUpdatedEvent",
    "DELETE,PriorAuthorityDocumentDeletedEvent"
  })
  void givenDocumentEventInsertFails_whenMutated_thenDraftAndEventsRollBack(
      String method, String eventType) {
    UUID priorAuthorityId = saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "existing.pdf");
    awaitDocument(priorAuthorityId, documentId);
    Map<String, Object> draftBefore = draftRow(priorAuthorityId);
    int eventsBefore = eventCount(priorAuthorityId);

    rejectDocumentEvent(priorAuthorityId, eventType);
    try {
      ResponseEntity<String> failedResponse = mutateDocument(priorAuthorityId, documentId, method);
      assertThat(failedResponse.getStatusCode().is5xxServerError())
          .withFailMessage(
              "Mutation response: %s %s", failedResponse.getStatusCode(), failedResponse.getBody())
          .isTrue();
    } finally {
      removeDocumentEventRejection();
    }

    assertThat(draftRow(priorAuthorityId)).isEqualTo(draftBefore);
    assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsBefore);
    assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.getDocumentId()).isEqualTo(documentId);
              assertThat(document.getFileName()).isEqualTo("existing.pdf");
              assertThat(document.getDocumentType()).isNull();
            });
    verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());

    ResponseEntity<String> retryResponse = mutateDocument(priorAuthorityId, documentId, method);
    HttpStatus expectedStatus =
        switch (method) {
          case "POST" -> HttpStatus.CREATED;
          case "PATCH" -> HttpStatus.OK;
          default -> HttpStatus.NO_CONTENT;
        };
    assertThat(retryResponse.getStatusCode()).isEqualTo(expectedStatus);
    assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsBefore + 1);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var documents = getPriorAuthority(priorAuthorityId).getUploadedDocuments();
              switch (method) {
                case "POST" ->
                    assertThat(documents)
                        .extracting(document -> document.getFileName())
                        .containsExactlyInAnyOrder("existing.pdf", "new.pdf");
                case "PATCH" ->
                    assertThat(documents)
                        .singleElement()
                        .satisfies(
                            document ->
                                assertThat(document.getDocumentType())
                                    .isEqualTo(DocumentType.GATEWAY_EVIDENCE));
                default -> assertThat(documents).isEmpty();
              }
            });
    if ("DELETE".equals(method)) {
      verify(sdsService).deleteEvidenceFile(priorAuthorityId, documentId, documentId + ".pdf");
    } else {
      verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
    }
  }

  @Test
  void givenConcurrentUploads_whenRegistered_thenBothDocumentsAndFilenamesSurvive()
      throws Exception {
    UUID priorAuthorityId = saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, null, null);
    CyclicBarrier storedFiles = new CyclicBarrier(2);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              storedFiles.await(10, TimeUnit.SECONDS);
              return new SdsUploadResult(null, null, "checksum");
            });
    int eventsBefore = eventCount(priorAuthorityId);

    List<ResponseEntity<String>> responses =
        race(
            () ->
                restTemplate.postForEntity(
                    uploadUrl(priorAuthorityId), uploadRequest("first.pdf"), String.class),
            () ->
                restTemplate.postForEntity(
                    uploadUrl(priorAuthorityId), uploadRequest("second.PDF"), String.class));

    assertThat(responses)
        .allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED));
    List<UUID> documentIds =
        responses.stream()
            .map(
                response ->
                    objectMapper
                        .readValue(response.getBody(), UploadPriorAuthorityDocumentResponse.class)
                        .getDocumentId())
            .toList();
    assertThat(documentIds).doesNotHaveDuplicates();
    assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsBefore + 2);
    for (int index = 0; index < documentIds.size(); index++) {
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT payload -> 'documentFilenames' ->> ? FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                  String.class,
                  documentIds.get(index).toString(),
                  priorAuthorityId))
          .isEqualTo(index == 0 ? "first.pdf" : "second.PDF");
    }
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var documents = getPriorAuthority(priorAuthorityId).getUploadedDocuments();
              assertThat(documents)
                  .extracting(document -> document.getDocumentId())
                  .containsExactlyInAnyOrderElementsOf(documentIds);
              assertThat(documents)
                  .extracting(document -> document.getFileName())
                  .containsExactlyInAnyOrder("first.pdf", "second.PDF");
            });
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void givenDocumentMutationRacesDraftSave_whenBothCommit_thenNeitherChangeIsLost(String method)
      throws Exception {
    UUID priorAuthorityId =
        saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, "Original justification", null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "existing.pdf");
    awaitDocument(priorAuthorityId, documentId);
    int eventsBefore = eventCount(priorAuthorityId);
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
        .upsert(any(), any(), any(), any(), any());

    List<ResponseEntity<String>> responses =
        race(
            () -> mutateDocument(priorAuthorityId, documentId, method),
            () ->
                restTemplate.exchange(
                    priorAuthorityUrl(priorAuthorityId),
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        SavePriorAuthorityDraftRequest.builder()
                            .justification("Concurrent justification")
                            .build(),
                        headers()),
                    String.class));

    HttpStatus mutationStatus =
        switch (method) {
          case "POST" -> HttpStatus.CREATED;
          case "PATCH" -> HttpStatus.OK;
          default -> HttpStatus.NO_CONTENT;
        };
    assertThat(responses.get(0).getStatusCode()).isEqualTo(mutationStatus);
    assertThat(responses.get(1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              PriorAuthorityResponse priorAuthority = getPriorAuthority(priorAuthorityId);
              assertThat(priorAuthority.getJustification()).isEqualTo("Concurrent justification");
              var documents = priorAuthority.getUploadedDocuments();
              switch (method) {
                case "POST" ->
                    assertThat(documents)
                        .extracting(document -> document.getFileName())
                        .containsExactlyInAnyOrder("existing.pdf", "new.pdf");
                case "PATCH" ->
                    assertThat(documents)
                        .singleElement()
                        .satisfies(
                            document -> {
                              assertThat(document.getDocumentId()).isEqualTo(documentId);
                              assertThat(document.getFileName()).isEqualTo("existing.pdf");
                              assertThat(document.getDocumentType())
                                  .isEqualTo(DocumentType.GATEWAY_EVIDENCE);
                            });
                default -> assertThat(documents).isEmpty();
              }
            });
    assertThat(
            objectMapper
                .readTree((String) draftRow(priorAuthorityId).get("payload"))
                .get("documentFilenames")
                .size())
        .isEqualTo("POST".equals(method) ? 2 : "DELETE".equals(method) ? 0 : 1);
    assertThat(eventCount(priorAuthorityId))
        .withFailMessage(
            "Both successful writes must be recorded in the Prior Authority event stream")
        .isEqualTo(eventsBefore + 2);
    assertThat(writeAttempts.get())
        .withFailMessage("One of the two stale writes must roll back and retry against fresh state")
        .isEqualTo(3);
    String eventPayload =
        jdbcTemplate.queryForObject(
            "SELECT convert_from(payload, 'UTF8') FROM axon.domain_event_entry"
                + " WHERE aggregate_identifier = ? AND payload_type LIKE '%PriorAuthorityDraftUpdatedEvent'",
            String.class, priorAuthorityId.toString());
    var eventJson = objectMapper.readTree(eventPayload);
    assertThat(eventJson.size()).isEqualTo(3);
    assertThat(eventJson.get("priorAuthorityId").asString()).isEqualTo(priorAuthorityId.toString());
    assertThat(eventJson.get("parentApplicationId").asString())
        .isEqualTo(getPriorAuthority(priorAuthorityId).getApplicationId().toString());
    assertThat(eventJson.get("occurredAt").asString()).isNotBlank();
    assertThat(eventPayload)
        .doesNotContain(
            "Concurrent justification", "Original justification", "existing.pdf", "new.pdf");
  }

  @Test
  void givenUploadPausedInSds_whenSubmissionCommits_thenLateRegistrationIsRejected()
      throws Exception {
    UUID priorAuthorityId =
        saveDraft(
            grantedApplication(),
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());
    CountDownLatch storedFile = new CountDownLatch(1);
    CountDownLatch releaseUpload = new CountDownLatch(1);
    AtomicReference<UUID> documentId = new AtomicReference<>();
    doAnswer(
            invocation -> {
              documentId.set(invocation.getArgument(1));
              storedFile.countDown();
              assertThat(releaseUpload.await(20, TimeUnit.SECONDS)).isTrue();
              return new SdsUploadResult(null, null, "checksum");
            })
        .when(sdsService)
        .saveEvidenceFile(any(), any(), any());
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<ResponseEntity<String>> upload =
          CompletableFuture.supplyAsync(
              () ->
                  restTemplate.postForEntity(
                      uploadUrl(priorAuthorityId), uploadRequest("late.pdf"), String.class),
              executor);
      assertThat(storedFile.await(10, TimeUnit.SECONDS)).isTrue();
      submitDraft(priorAuthorityId);
      assertThat(awaitSubmittedPriorAuthority(priorAuthorityId).getUploadedDocuments())
          .isNullOrEmpty();
      int eventsAfterSubmission = eventCount(priorAuthorityId);

      releaseUpload.countDown();
      ResponseEntity<String> response = upload.get(20, TimeUnit.SECONDS);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("prior authority draft");
      assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsAfterSubmission);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                  Integer.class,
                  priorAuthorityId))
          .isZero();
      assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments()).isNullOrEmpty();
      assertThat(documentId.get()).isNotNull();
      verify(sdsService, never()).deleteEvidenceFile(any(), any(), any());
    } finally {
      releaseUpload.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void givenConcurrentDeletes_whenHandled_thenOneDeletionEventAndCleanupOccur() throws Exception {
    UUID priorAuthorityId = saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    awaitDocument(priorAuthorityId, documentId);
    int eventsBefore = eventCount(priorAuthorityId);

    List<ResponseEntity<String>> responses =
        race(
            () -> mutateDocument(priorAuthorityId, documentId, "DELETE"),
            () -> mutateDocument(priorAuthorityId, documentId, "DELETE"));

    assertThat(responses)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND);
    assertThat(eventCount(priorAuthorityId)).isEqualTo(eventsBefore + 1);
    verify(sdsService).deleteEvidenceFile(priorAuthorityId, documentId, documentId + ".pdf");
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments()).isEmpty());
    assertThat(
            objectMapper
                .readTree((String) draftRow(priorAuthorityId).get("payload"))
                .get("documentFilenames")
                .size())
        .isZero();
  }

  @Test
  void givenTypeUpdateRacesDelete_whenHandled_thenDocumentIsNotResurrected() throws Exception {
    UUID priorAuthorityId = saveDraft(grantedApplication(), PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    awaitDocument(priorAuthorityId, documentId);
    int eventsBefore = eventCount(priorAuthorityId);

    List<ResponseEntity<String>> responses =
        race(
            () -> mutateDocument(priorAuthorityId, documentId, "PATCH"),
            () -> mutateDocument(priorAuthorityId, documentId, "DELETE"));

    assertThat(responses.get(0).getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    assertThat(responses.get(1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(eventCount(priorAuthorityId))
        .isEqualTo(eventsBefore + (responses.get(0).getStatusCode() == HttpStatus.OK ? 2 : 1));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments()).isEmpty());
    assertThat(
            objectMapper
                .readTree((String) draftRow(priorAuthorityId).get("payload"))
                .get("documentFilenames")
                .size())
        .isZero();
    verify(sdsService).deleteEvidenceFile(priorAuthorityId, documentId, documentId + ".pdf");
  }

  @Test
  void givenDeletedDocument_whenSubmitPriorAuthorityDraft_thenSubmittedPayloadExcludesDocument() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange(
            deleteDocumentUrl(priorAuthorityId, documentId),
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode())
        .withFailMessage("Submit response: %s", submitResponse.getBody())
        .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);

    PriorAuthorityResponse priorAuthority = awaitSubmittedPriorAuthority(priorAuthorityId);
    assertThat(priorAuthority.getUploadedDocuments()).isEmpty();
  }

  @Test
  void givenNonexistentPriorAuthority_whenDeletePriorAuthorityDocument_thenReturnsNotFound() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            deleteDocumentUrl(UUID.randomUUID(), UUID.randomUUID()),
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenSubmittedPriorAuthority_whenDeletePriorAuthorityDocument_thenReturnsNotFound() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    ResponseEntity<Void> updateDocumentTypeResponse =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE),
                headers()),
            Void.class);
    assertThat(updateDocumentTypeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode())
        .withFailMessage("Submit response: %s", submitResponse.getBody())
        .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);

    ResponseEntity<String> deleteResponse =
        restTemplate.exchange(
            deleteDocumentUrl(priorAuthorityId, documentId),
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void
      givenDraftViolatesSchema_whenSubmitPriorAuthorityDraft_thenReturnsBadRequestAndDraftPersists() {
    UUID applicationId = grantedApplication();
    // Missing justification, which the full PriorAuthority schema requires at submit time.
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isZero();
  }

  @Test
  void givenNoDraftInProgress_whenSubmitPriorAuthorityDraft_thenReturnsNotFound() {
    UUID nonexistentPriorAuthorityId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(nonexistentPriorAuthorityId),
            new HttpEntity<>(null, headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenNoDraft_whenGetPriorAuthority_thenReturnsNotFound() {
    UUID nonexistentPriorAuthorityId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            priorAuthorityUrl(nonexistentPriorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private UUID saveDraft(
      UUID applicationId,
      PriorAuthorityType priorAuthorityType,
      String justification,
      DisbursementDetails disbursement) {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(priorAuthorityType)
            .justification(justification)
            .disbursementDetails(disbursement)
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    UUID priorAuthorityId =
        objectMapper
            .readValue(response.getBody(), SavePriorAuthorityDraftResponse.class)
            .getPriorAuthorityId();
    projectionAwaiter.awaitPriorAuthority(priorAuthorityId);
    return priorAuthorityId;
  }

  private PriorAuthorityResponse getPriorAuthority(UUID priorAuthorityId) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(response.getStatusCode())
        .withFailMessage("GET response: %s", response.getBody())
        .isEqualTo(HttpStatus.OK);
    return objectMapper.readValue(response.getBody(), PriorAuthorityResponse.class);
  }

  private int eventCount(UUID priorAuthorityId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
        Integer.class,
        priorAuthorityId.toString());
  }

  private Map<String, Object> draftRow(UUID priorAuthorityId) {
    return jdbcTemplate.queryForMap(
        "SELECT payload::text AS payload, payload_hash, created_at, updated_at"
            + " FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
        priorAuthorityId);
  }

  private List<ResponseEntity<String>> race(
      Supplier<ResponseEntity<String>> first, Supplier<ResponseEntity<String>> second)
      throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<String>> firstResult = concurrent(executor, barrier, first);
      CompletableFuture<ResponseEntity<String>> secondResult =
          concurrent(executor, barrier, second);
      return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private CompletableFuture<ResponseEntity<String>> concurrent(
      ExecutorService executor, CyclicBarrier barrier, Supplier<ResponseEntity<String>> operation) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            barrier.await(10, TimeUnit.SECONDS);
            return operation.get();
          } catch (Exception failure) {
            throw new IllegalStateException("Concurrent request failed", failure);
          }
        },
        executor);
  }

  private ResponseEntity<String> mutateDocument(
      UUID priorAuthorityId, UUID documentId, String method) {
    return switch (method) {
      case "POST" ->
          restTemplate.postForEntity(
              uploadUrl(priorAuthorityId), uploadRequest("new.pdf"), String.class);
      case "PATCH" ->
          restTemplate.exchange(
              documentUrl(priorAuthorityId, documentId),
              HttpMethod.PATCH,
              new HttpEntity<>(
                  new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE),
                  headers()),
              String.class);
      default ->
          restTemplate.exchange(
              documentUrl(priorAuthorityId, documentId),
              HttpMethod.DELETE,
              new HttpEntity<>(headers()),
              String.class);
    };
  }

  private void rejectDocumentEvent(UUID priorAuthorityId, String eventType) {
    jdbcTemplate.execute(
        """
            CREATE OR REPLACE FUNCTION axon.reject_test_prior_authority_document()
            RETURNS trigger AS $$
            BEGIN
                IF NEW.aggregate_identifier = '%s' AND NEW.payload_type LIKE '%%%s' THEN
                    RAISE EXCEPTION 'forced prior authority document event insertion failure';
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """
            .formatted(priorAuthorityId, eventType));
    jdbcTemplate.execute(
        """
            CREATE TRIGGER reject_test_prior_authority_document
            BEFORE INSERT ON axon.domain_event_entry
            FOR EACH ROW EXECUTE FUNCTION axon.reject_test_prior_authority_document()
            """);
  }

  private void removeDocumentEventRejection() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS reject_test_prior_authority_document ON axon.domain_event_entry");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_prior_authority_document()");
  }

  private ResponseEntity<Void> deleteDocument(UUID priorAuthorityId, UUID documentId) {
    return restTemplate.exchange(
        deleteDocumentUrl(priorAuthorityId, documentId),
        HttpMethod.DELETE,
        new HttpEntity<>(headers()),
        Void.class);
  }

  private void updateDocumentType(UUID priorAuthorityId, UUID documentId) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE),
                headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(response.getBody()).get("documentId").asString())
        .isEqualTo(documentId.toString());
    assertThat(objectMapper.readTree(response.getBody()).get("updatedAt").asString()).isNotBlank();
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments())
                    .anySatisfy(
                        document -> {
                          assertThat(document.getDocumentId()).isEqualTo(documentId);
                          assertThat(document.getDocumentType())
                              .isEqualTo(DocumentType.GATEWAY_EVIDENCE);
                        }));
  }

  private void submitDraft(UUID priorAuthorityId) {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(response.getStatusCode())
        .withFailMessage("Submit response: %s", response.getBody())
        .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
  }

  private PriorAuthorityResponse awaitSubmittedPriorAuthority(UUID priorAuthorityId) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(getPriorAuthority(priorAuthorityId).getStatus())
                    .isEqualTo(PriorAuthorityResponse.StatusEnum.SUBMITTED));
    return getPriorAuthority(priorAuthorityId);
  }

  private void awaitDocument(UUID priorAuthorityId, UUID documentId) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(getPriorAuthority(priorAuthorityId).getUploadedDocuments())
                    .anySatisfy(
                        document -> assertThat(document.getDocumentId()).isEqualTo(documentId)));
  }

  private DisbursementDetails validDisbursementRequest() {
    return DisbursementDetails.builder()
        .disbursementPurpose("Court interpreter")
        .disbursementAmount(BigDecimal.valueOf(150.0))
        .build();
  }

  private void createApplication(UUID applicationId, UUID applyProceedingId) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, applyProceedingId), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private UUID grantedApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, UUID.randomUUID());
    projectionAwaiter.awaitApplication(applicationId);
    grantApplication(applicationId);
    projectionAwaiter.awaitApplicationVersion(applicationId, 1L);
    return applicationId;
  }

  private void grantApplication(UUID applicationId) {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(
                new AutoGrantedOutcomeRequest(
                    AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
                headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private String saveDraftUrl() {
    return "http://localhost:" + port + "/api/v0/prior-authorities";
  }

  private String priorAuthorityUrl(UUID priorAuthorityId) {
    return "http://localhost:" + port + "/api/v0/prior-authorities/" + priorAuthorityId;
  }

  private String submitUrl(UUID priorAuthorityId) {
    return priorAuthorityUrl(priorAuthorityId) + "/submit";
  }

  private String uploadUrl(UUID priorAuthorityId) {
    return priorAuthorityUrl(priorAuthorityId) + "/documents";
  }

  private String documentUrl(UUID priorAuthorityId, UUID documentId) {
    return uploadUrl(priorAuthorityId) + "/" + documentId;
  }

  private String documentContentUrl(UUID priorAuthorityId, UUID documentId) {
    return documentUrl(priorAuthorityId, documentId);
  }

  private String deleteDocumentUrl(UUID priorAuthorityId, UUID documentId) {
    return priorAuthorityUrl(priorAuthorityId) + "/documents/" + documentId;
  }

  private UUID uploadDocument(UUID priorAuthorityId, String filename) {
    ResponseEntity<String> uploadResponse =
        restTemplate.postForEntity(
            uploadUrl(priorAuthorityId), uploadRequest(filename), String.class);
    assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(uploadResponse.getBody(), UploadPriorAuthorityDocumentResponse.class)
        .getDocumentId();
  }

  private HttpEntity<MultiValueMap<String, Object>> uploadRequest(String filename) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.APPLICATION_PDF);
    body.add(
        "file",
        new HttpEntity<>(
            new ByteArrayResource("%PDF-1.4\ncontent".getBytes()) {
              @Override
              public String getFilename() {
                return filename;
              }
            },
            fileHeaders));

    HttpHeaders multipartHeaders = new HttpHeaders();
    multipartHeaders.set("X-Service-Name", "CIVIL_APPLY");
    multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    multipartHeaders.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return new HttpEntity<>(body, multipartHeaders);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
