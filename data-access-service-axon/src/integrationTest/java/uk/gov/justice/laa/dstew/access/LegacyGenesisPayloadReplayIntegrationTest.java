package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.DocumentType;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.testutils.EventTypeNames;

/**
 * ADR 0006 rollout without a data reset: events stored before their genesis field existed must
 * still source their aggregates from PostgreSQL. Each stream is written by the running service and
 * the new field is then stripped from the stored JSON, leaving the bytes the previous release
 * wrote, before the aggregate is loaded and asked to decide.
 */
class LegacyGenesisPayloadReplayIntegrationTest extends BaseIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AxonConfiguration axonConfiguration;

  @MockitoBean private SdsService sdsService;

  @Test
  void givenLegacyApplicationAssignmentEvents_whenSourced_thenRestoresAssignmentAndDecides() {
    UUID applicationId = UUID.randomUUID();
    createManualApplication(applicationId);
    awaitInWorkList(applicationId);
    assertThat(assign(applicationId, 0L)).isEqualTo(HttpStatus.OK);
    assertThat(unassign(applicationId, 1L)).isEqualTo(HttpStatus.OK);

    storeAsLegacy(applicationId, WorkItemAssigned.class, "applicationId");
    storeAsLegacy(applicationId, WorkItemUnassigned.class, "applicationId");

    ApplicationAggregate aggregate = load(ApplicationAggregate.class, applicationId);
    assertThat(aggregate.getState().getAssignmentVersion()).isEqualTo(2L);
    assertThat(aggregate.getState().getCaseworkerId()).isNull();
    assertThat(assign(applicationId, 1L)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(assign(applicationId, 2L)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void givenLegacyPriorAuthorityEvents_whenSourced_thenRestoresDocumentsAndAssignmentAndDecides() {
    UUID applicationId = UUID.randomUUID();
    createGrantedApplication(applicationId);
    UUID priorAuthorityId = createDraft(applicationId);
    updateDraft(priorAuthorityId);
    UUID documentId = uploadDocument(priorAuthorityId);
    updateDocumentType(priorAuthorityId, documentId);
    submit(priorAuthorityId);
    assertThat(assign(priorAuthorityId, 0L)).isEqualTo(HttpStatus.OK);

    storeAsLegacy(
        priorAuthorityId, PriorAuthorityDocumentTypeUpdatedEvent.class, "parentApplicationId");
    storeAsLegacy(priorAuthorityId, WorkItemAssigned.class, "applicationId");
    assertThat(storedPayloads(priorAuthorityId, PriorAuthorityDraftUpdatedEvent.class)).hasSize(1);

    PriorAuthorityAggregate aggregate = load(PriorAuthorityAggregate.class, priorAuthorityId);
    assertThat(aggregate.getApplicationId()).isEqualTo(applicationId);
    assertThat(aggregate.getState().isSubmitted()).isTrue();
    assertThat(aggregate.getAssignmentVersion()).isEqualTo(1L);
    assertThat(aggregate.getCaseworkerId()).isEqualTo(TestJwtDecoderConfig.CASEWORKER_ID);
    assertThat(aggregate.getState().getUploadedDocuments())
        .singleElement()
        .extracting(DocumentMetadata::documentId, DocumentMetadata::documentType)
        .containsExactly(documentId, DocumentType.GATEWAY_EVIDENCE.getValue());
    assertThat(unassign(priorAuthorityId, 0L)).isEqualTo(HttpStatus.CONFLICT);
    assertThat(unassign(priorAuthorityId, 1L)).isEqualTo(HttpStatus.OK);
  }

  /** Rewrites each stored event of the type to the JSON written before {@code field} existed. */
  private void storeAsLegacy(UUID streamId, Class<?> eventType, String field) {
    List<Map<String, Object>> rows = storedPayloads(streamId, eventType);
    assertThat(rows).isNotEmpty();
    for (Map<String, Object> row : rows) {
      ObjectNode payload = (ObjectNode) objectMapper.readTree((byte[]) row.get("payload"));
      assertThat(payload.hasNonNull(field)).isTrue();
      payload.remove(field);
      jdbcTemplate.update(
          "UPDATE axon.domain_event_entry SET payload = ? WHERE global_index = ?",
          objectMapper.writeValueAsBytes(payload),
          row.get("global_index"));
    }
    assertThat(storedPayloads(streamId, eventType))
        .allSatisfy(
            row ->
                assertThat(objectMapper.readTree((byte[]) row.get("payload")).has(field))
                    .isFalse());
  }

  private List<Map<String, Object>> storedPayloads(UUID streamId, Class<?> eventType) {
    return jdbcTemplate.queryForList(
        "SELECT global_index, payload FROM axon.domain_event_entry"
            + " WHERE aggregate_identifier = ? AND payload_type = ? ORDER BY global_index",
        streamId.toString(),
        EventTypeNames.of(eventType));
  }

  private <T> T load(Class<T> type, UUID id) {
    StateManager stateManager = axonConfiguration.getComponent(StateManager.class);
    return axonConfiguration
        .getComponent(UnitOfWorkFactory.class)
        .create()
        .executeWithResult(context -> stateManager.loadEntity(type, id, context))
        .join();
  }

  private void createManualApplication(UUID applicationId) {
    createApplication(applicationId);
    autoGrantOutcome(applicationId, new ManualOutcomeRequest(AutoGrantOutcome.MANUAL));
  }

  private void createGrantedApplication(UUID applicationId) {
    createApplication(applicationId);
    autoGrantOutcome(
        applicationId,
        new AutoGrantedOutcomeRequest(
            AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")));
  }

  private void createApplication(UUID applicationId) {
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            url("/api/v0/applications"),
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private void autoGrantOutcome(UUID applicationId, Object outcome) {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(outcome, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private void awaitInWorkList(UUID itemId) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        restTemplate
                            .exchange(
                                url("/api/v0/work-list"),
                                HttpMethod.GET,
                                new HttpEntity<>(headers()),
                                WorkListResponse.class)
                            .getBody()
                            .getItems())
                    .anySatisfy(item -> assertThat(item.getItemId()).isEqualTo(itemId)));
  }

  private UUID createDraft(UUID applicationId) {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .justification("Interpreter costs for proceedings")
            .disbursementDetails(disbursement())
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            url("/api/v0/prior-authorities"), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), SavePriorAuthorityDraftResponse.class)
        .getPriorAuthorityId();
  }

  private void updateDraft(UUID priorAuthorityId) {
    SavePriorAuthorityDraftRequest request =
        SavePriorAuthorityDraftRequest.builder()
            .justification("Updated interpreter costs")
            .disbursementDetails(disbursement())
            .build();
    ResponseEntity<Void> response =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.PUT,
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private UUID uploadDocument(UUID priorAuthorityId) {
    when(sdsService.saveEvidenceFile(any(), any(), any()))
        .thenReturn(new SdsUploadResult(null, null, "checksum"));
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.APPLICATION_PDF);
    body.add(
        "file",
        new HttpEntity<>(
            new ByteArrayResource("%PDF-1.4\ncontent".getBytes()) {
              @Override
              public String getFilename() {
                return "evidence.pdf";
              }
            },
            fileHeaders));
    HttpHeaders headers = headers();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(priorAuthorityId) + "/documents",
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), UploadPriorAuthorityDocumentResponse.class)
        .getDocumentId();
  }

  private void updateDocumentType(UUID priorAuthorityId, UUID documentId) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId) + "/documents/" + documentId,
            HttpMethod.PATCH,
            new HttpEntity<>(
                new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE),
                headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private void submit(UUID priorAuthorityId) {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            priorAuthorityUrl(priorAuthorityId) + "/submit",
            new HttpEntity<>(null, headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private HttpStatus assign(UUID itemId, long expectedAssignmentVersion) {
    return (HttpStatus)
        restTemplate
            .exchange(
                url("/api/v0/work-list/" + itemId + "/assign"),
                HttpMethod.POST,
                new HttpEntity<>(new WorkListAssignRequest(expectedAssignmentVersion), headers()),
                Void.class)
            .getStatusCode();
  }

  private HttpStatus unassign(UUID itemId, long expectedAssignmentVersion) {
    return (HttpStatus)
        restTemplate
            .exchange(
                url("/api/v0/work-list/" + itemId + "/unassign"),
                HttpMethod.POST,
                new HttpEntity<>(new WorkListUnassignRequest(expectedAssignmentVersion), headers()),
                Void.class)
            .getStatusCode();
  }

  private String priorAuthorityUrl(UUID priorAuthorityId) {
    return url("/api/v0/prior-authorities/" + priorAuthorityId);
  }

  private static DisbursementDetails disbursement() {
    return DisbursementDetails.builder()
        .disbursementPurpose("Court interpreter")
        .disbursementAmount(BigDecimal.valueOf(150.0))
        .build();
  }
}
