package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
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
        .thenReturn(new DocumentUploadResponse().checksum("checksum"));

    ResponseEntity<String> uploadResponse =
        restTemplate.postForEntity(
            uploadUrl(priorAuthorityId), uploadRequest("evidence.pdf"), String.class);
    assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID documentId =
        objectMapper
            .readValue(uploadResponse.getBody(), UploadPriorAuthorityDocumentResponse.class)
            .getDocumentId();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT original_filename FROM axon.uploaded_documents WHERE document_id = ?",
                String.class,
                documentId))
        .isEqualTo("evidence.pdf");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload -> 'content' -> 'uploadedDocuments'"
                    + " FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                String.class,
                priorAuthorityId))
        .isNull();

    ResponseEntity<Void> updateDocumentTypeResponse =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(
                    PriorAuthorityDocumentType.GATEWAY_EVIDENCE),
                headers()),
            Void.class);
    assertThat(updateDocumentTypeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode())
        .withFailMessage("Submit response: %s", submitResponse.getBody())
        .isIn(HttpStatus.OK, HttpStatus.ACCEPTED);

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
    assertThat(priorAuthority.getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.getFileName()).isEqualTo("evidence.pdf");
              assertThat(document.getDocumentType())
                  .isEqualTo(PriorAuthorityDocumentType.GATEWAY_EVIDENCE);
            });
  }

  @Test
  void givenUploadedDocument_whenDownloaded_thenStreamsContentWithOriginalFilename() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new DocumentUploadResponse().checksum("checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    byte[] content = "%PDF-1.4\ncontent".getBytes();
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, "evidence.pdf"))
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
        .isEqualTo("evidence.pdf");
    assertThat(response.getBody()).containsExactly(content);
    verify(sdsService).getEvidenceFile(priorAuthorityId, documentId, "evidence.pdf");
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

  @Test
  void
      givenDraftWithUploadedDocument_whenDeletePriorAuthorityDocument_thenReturnsNoContentAndRemovesDocument() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new DocumentUploadResponse().checksum("checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.domain_event_entry"
                    + " WHERE aggregate_identifier = ?"
                    + " AND payload_type = ?",
                Integer.class,
                priorAuthorityId.toString(),
                "uk.gov.justice.laa.dstew.access.command.application.priorauthority"
                    + ".PriorAuthorityDocumentUploadedEvent"))
        .isEqualTo(1);

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange(
            deleteDocumentUrl(priorAuthorityId, documentId),
            HttpMethod.DELETE,
            new HttpEntity<>(headers()),
            Void.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));

    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
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
        .thenReturn(new DocumentUploadResponse().checksum("checksum"));
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
        .thenReturn(new DocumentUploadResponse().checksum("checksum"));
    UUID documentId = uploadDocument(priorAuthorityId, "evidence.pdf");
    ResponseEntity<Void> updateDocumentTypeResponse =
        restTemplate.exchange(
            documentUrl(priorAuthorityId, documentId),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(
                    PriorAuthorityDocumentType.GATEWAY_EVIDENCE),
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
    return documentUrl(priorAuthorityId, documentId) + "/content";
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
    body.add(
        "file",
        new ByteArrayResource("%PDF-1.4\ncontent".getBytes()) {
          @Override
          public String getFilename() {
            return filename;
          }
        });

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
