package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

@DisplayName("PriorAuthorityAggregate")
class PriorAuthorityAggregateTest {

  private static final UUID PA_ID = UUID.randomUUID();
  private static final UUID APP_ID = UUID.randomUUID();
  private static final String PA_TYPE = "testType";
  private static final String OFFICE_CODE = "1A001B";
  private static final int SCHEMA_VERSION = 1;
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName("draft-start event restores identity, office code, and type")
  void draftStartedEventRestoresState() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW, OFFICE_CODE));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getApplicationId()).isEqualTo(APP_ID);
    assertThat(aggregate.getOfficeCode()).isEqualTo(OFFICE_CODE);
    assertThat(aggregate.getPriorAuthorityType()).isEqualTo(PA_TYPE);
  }

  @Test
  @DisplayName("historical draft updates replay without changing identity, lifecycle, or documents")
  void givenHistoricalDraftUpdateJson_whenReplayed_thenPreservesAggregateState() {
    ObjectMapper objectMapper = new ObjectMapper();
    String historicalJson =
        """
      {
        "priorAuthorityId": "%s",
        "parentApplicationId": "%s",
        "occurredAt": "2026-10-01T10:00:00Z"
      }
      """
            .formatted(PA_ID, APP_ID);
    PriorAuthorityDraftUpdatedEvent event =
        objectMapper.readValue(historicalJson, PriorAuthorityDraftUpdatedEvent.class);
    assertThat(event.priorAuthorityId()).isEqualTo(PA_ID);
    assertThat(event.parentApplicationId()).isEqualTo(APP_ID);
    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(event)))
        .isEqualTo(objectMapper.readTree(historicalJson));

    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW, OFFICE_CODE));
    UUID documentId = UUID.randomUUID();
    aggregate.on(
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, documentId, NOW, 1024L, "application/pdf", "checksum123", APP_ID, "service"));
    aggregate.on(event);
    aggregate.on(new PriorAuthorityDocumentTypeUpdatedEvent(PA_ID, documentId, "INVOICE", NOW));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getApplicationId()).isEqualTo(APP_ID);
    assertThat(aggregate.getOfficeCode()).isEqualTo(OFFICE_CODE);
    assertThat(aggregate.getPriorAuthorityType()).isEqualTo(PA_TYPE);
    assertThat(aggregate.getDataVersion()).isZero();
    assertThat(aggregate.getState().isSubmitted()).isFalse();
    assertThat(aggregate.getState().isDecided()).isFalse();
    assertThat(aggregate.getState().getUploadedDocuments())
        .containsExactly(
            new DocumentMetadata(
                documentId,
                "INVOICE",
                NOW,
                1024L,
                "application/pdf",
                "checksum123",
                "service",
                false));
  }

  @Test
  @DisplayName("submission event restores the data version")
  void submittedEventRestoresDataVersion() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new PriorAuthoritySubmittedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, 1L, null, NOW));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getDataVersion()).isEqualTo(1L);
    assertThat(aggregate.getState().isSubmitted()).isTrue();
  }

  @Test
  @DisplayName("decision event restores the data version and decided lifecycle")
  void decisionEventRestoresDataVersion() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new PriorAuthorityDecisionMadeEvent(
            PA_ID,
            APP_ID,
            PA_TYPE,
            2L,
            "APPROVED",
            "test justification",
            BigDecimal.TEN,
            NOW,
            NOW));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getDataVersion()).isEqualTo(2L);
    assertThat(aggregate.getState().isDecided()).isTrue();
  }

  @Test
  @DisplayName("upload event restores filename-free document metadata")
  void documentUploadedEventRestoresMetadata() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));
    UUID documentId = UUID.randomUUID();
    aggregate.on(
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, documentId, NOW, 1024L, "application/pdf", "checksum123", APP_ID, "service"));

    assertThat(aggregate.getState().getUploadedDocuments())
        .contains(
            new DocumentMetadata(
                documentId, null, NOW, 1024L, "application/pdf", "checksum123", "service", false));
  }

  @Test
  @DisplayName("delete event marks document metadata deleted")
  void documentDeletedEventMarksMetadataDeleted() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));
    UUID documentId = UUID.randomUUID();
    aggregate.on(
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, documentId, NOW, 1024L, "application/pdf", "checksum123", APP_ID, "service"));

    aggregate.on(new PriorAuthorityDocumentDeletedEvent(PA_ID, documentId, NOW, APP_ID));

    assertThat(aggregate.getState().getUploadedDocuments())
        .contains(
            new DocumentMetadata(
                documentId, null, NOW, 1024L, "application/pdf", "checksum123", "service", true));
  }

  @Test
  @DisplayName("document-type event updates metadata")
  void documentTypeUpdatedEventUpdatesMetadata() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(new PriorAuthorityDraftStartedEvent(PA_ID, APP_ID, PA_TYPE, SCHEMA_VERSION, NOW));
    UUID documentId = UUID.randomUUID();
    aggregate.on(
        new PriorAuthorityDocumentUploadedEvent(
            PA_ID, documentId, NOW, 1024L, "application/pdf", "checksum123", APP_ID, "service"));

    aggregate.on(new PriorAuthorityDocumentTypeUpdatedEvent(PA_ID, documentId, "INVOICE", NOW));

    assertThat(aggregate.getPriorAuthorityId()).isEqualTo(PA_ID);
    assertThat(aggregate.getState().getUploadedDocuments())
        .contains(
            new DocumentMetadata(
                documentId,
                "INVOICE",
                NOW,
                1024L,
                "application/pdf",
                "checksum123",
                "service",
                false));
  }

  @Test
  @DisplayName("assignment and unassignment events restore assignment state")
  void assignmentEventsRestoreState() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new WorkItemAssigned(PA_ID, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, CASEWORKER_ID, NOW));

    assertThat(aggregate.getCaseworkerId()).isEqualTo(CASEWORKER_ID);
    assertThat(aggregate.getAssignmentVersion()).isEqualTo(1L);

    aggregate.on(new WorkItemUnassigned(PA_ID, WorkItemType.PRIOR_AUTHORITY, 1L, 2L, NOW));

    assertThat(aggregate.getCaseworkerId()).isNull();
    assertThat(aggregate.getAssignmentVersion()).isEqualTo(2L);
  }
}
