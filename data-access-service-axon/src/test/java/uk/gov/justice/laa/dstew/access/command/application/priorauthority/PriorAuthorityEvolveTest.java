package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Unit tests for {@link PriorAuthorityEvolve}. */
class PriorAuthorityEvolveTest {

  @Test
  void givenDraftStartedEvent_whenApply_thenMutatesStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDraftStartedEvent event =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 3, occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
    assertThat(state.isSubmitted()).isFalse();
    assertThat(state.isDecided()).isFalse();
    assertThat(state.isDraftOpen()).isTrue();
  }

  @Test
  void givenSubmittedEvent_whenApply_thenMutatesStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 3, 0L, 0L, occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
    assertThat(state.getDataVersion()).isEqualTo(0L);
    assertThat(state.isSubmitted()).isTrue();
    assertThat(state.isDecided()).isFalse();
    assertThat(state.isDraftOpen()).isFalse();
  }

  @Test
  void givenDecisionMadeEvent_whenApply_thenMutatesDecisionStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDecisionMadeEvent event =
        new PriorAuthorityDecisionMadeEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1L,
            "REFUSED",
            "Reason",
            null,
            null,
            occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getDataVersion()).isEqualTo(1L);
    assertThat(state.isSubmitted()).isTrue();
    assertThat(state.isDecided()).isTrue();
  }

  @Test
  void givenGenericAssignmentEvents_whenApply_thenUpdatesAndClearsTheAssignmentState() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemAssigned(
            workItemId, WorkItemType.PRIOR_AUTHORITY, 3L, 4L, caseworkerId, occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(4L);
    assertThat(state.getCaseworkerId()).isEqualTo(caseworkerId);

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemUnassigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 3L, 5L, occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(5L);
    assertThat(state.getCaseworkerId()).isNull();
  }

  @Test
  void givenDocumentUploadedEvent_whenApply_thenTracksDocumentFacts() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:00:00Z");

    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentUploadedEvent(
            UUID.randomUUID(),
            documentId,
            uploadedAt,
            10L,
            "application/pdf",
            "sum",
            "PDF",
            "CIVIL_APPLY",
            "INVOICE",
            UUID.randomUUID()));

    assertThat(state.getUploadedDocuments())
        .singleElement()
        .satisfies(
            data -> {
              assertThat(data.documentId()).isEqualTo(documentId);
              assertThat(data.fileType()).isEqualTo("PDF");
              assertThat(data.contentType()).isEqualTo("application/pdf");
              assertThat(data.sourceService()).isEqualTo("CIVIL_APPLY");
              assertThat(data.documentType()).isEqualTo("INVOICE");
              assertThat(data.uploadedAt()).isEqualTo(uploadedAt);
              assertThat(data.deletedAt()).isNull();
            });
  }

  @Test
  void givenDocumentDeletedEvent_whenApply_thenMarksDocumentDeleted() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:00:00Z");
    state
        .getUploadedDocuments()
        .add(
            new UploadedDocumentData(
                documentId, 10L, "PDF", "application/pdf", "CIVIL_APPLY", null, uploadedAt, null));

    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentDeletedEvent(
            UUID.randomUUID(), documentId, Instant.now(), UUID.randomUUID()));

    assertThat(state.getUploadedDocuments())
        .singleElement()
        .satisfies(data -> assertThat(data.deletedAt()).isNotNull());
  }

  @Test
  void givenDocumentTypeUpdatedEvent_whenApply_thenUpdatesDocumentType() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID documentId = UUID.randomUUID();
    state
        .getUploadedDocuments()
        .add(
            new UploadedDocumentData(
                documentId,
                10L,
                "PDF",
                "application/pdf",
                "CIVIL_APPLY",
                "INVOICE",
                Instant.now(),
                null));

    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentTypeUpdatedEvent(
            UUID.randomUUID(), documentId, "GATEWAY_EVIDENCE", Instant.now()));

    assertThat(state.getUploadedDocuments())
        .singleElement()
        .satisfies(data -> assertThat(data.documentType()).isEqualTo("GATEWAY_EVIDENCE"));
  }
}
