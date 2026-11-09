package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

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
            priorAuthorityId, applicationId, "EXPERT", 3, occurredAt, "1A001B");

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getOfficeCode()).isEqualTo("1A001B");
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
    assertThat(state.isSubmitted()).isFalse();
    assertThat(state.isDecided()).isFalse();
  }

  @Test
  void givenSubmittedEvent_whenApply_thenMutatesStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 3, 0L, occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
    assertThat(state.getDataVersion()).isEqualTo(0L);
    assertThat(state.isSubmitted()).isTrue();
    assertThat(state.isDecided()).isFalse();
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
            workItemId,
            WorkItemType.PRIOR_AUTHORITY,
            UUID.randomUUID(),
            3L,
            4L,
            caseworkerId,
            occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(4L);
    assertThat(state.getCaseworkerId()).isEqualTo(caseworkerId);

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemUnassigned(
            workItemId, WorkItemType.PRIOR_AUTHORITY, UUID.randomUUID(), 3L, 5L, occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(5L);
    assertThat(state.getCaseworkerId()).isNull();
  }

  @Test
  void givenDocumentEvents_whenApplied_thenKeepsOrderedMetadataWithDeletedFlagAndType() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-08-01T10:00:00Z");

    PriorAuthorityEvolve.apply(state, uploaded(priorAuthorityId, firstId, uploadedAt));
    PriorAuthorityEvolve.apply(
        state, uploaded(priorAuthorityId, secondId, uploadedAt.plusSeconds(1)));
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentTypeUpdatedEvent(
            priorAuthorityId,
            secondId,
            "EXPERT_REPORT",
            uploadedAt.plusSeconds(2),
            UUID.randomUUID()));
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentDeletedEvent(
            priorAuthorityId, firstId, uploadedAt.plusSeconds(3), UUID.randomUUID()));

    assertThat(state.getUploadedDocuments())
        .containsExactly(
            new DocumentMetadata(
                firstId, null, uploadedAt, 10L, "application/pdf", "sum", "CIVIL_APPLY", true),
            new DocumentMetadata(
                secondId,
                "EXPERT_REPORT",
                uploadedAt.plusSeconds(1),
                10L,
                "application/pdf",
                "sum",
                "CIVIL_APPLY",
                false));
  }

  @Test
  void givenDraftStartedAndUpdated_whenApplied_thenDraftCarriesLatestContentAndFilenames() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID keptId = UUID.randomUUID();
    UUID deletedId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant updatedAt = startedAt.plusSeconds(60);
    PriorAuthorityContent updated =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Updated", null, null, null);

    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1,
            startedAt,
            "1A001B",
            Erasable.of(
                new PriorAuthorityContent(PriorAuthorityType.EXPERT, "First", null, null, null)),
            Erasable.of("{\"first\":true}")));
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftUpdatedEvent(
            priorAuthorityId,
            applicationId,
            updatedAt,
            Erasable.of(updated),
            Erasable.of("{\"updated\":true}")));
    PriorAuthorityEvolve.apply(state, uploaded(priorAuthorityId, keptId, startedAt, "kept.pdf"));
    PriorAuthorityEvolve.apply(
        state, uploaded(priorAuthorityId, deletedId, startedAt, "deleted.pdf"));
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentDeletedEvent(
            priorAuthorityId, deletedId, updatedAt, UUID.randomUUID()));

    assertThat(state.draft())
        .contains(
            new PriorAuthorityDataPayload(
                priorAuthorityId,
                applicationId,
                updated,
                "{\"updated\":true}",
                updatedAt,
                null,
                "1A001B",
                Map.of(keptId, "kept.pdf")));
  }

  @Test
  void givenErasedDraftContent_whenDraftRead_thenEmpty() {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EXPERT",
            1,
            Instant.parse("2026-08-01T10:00:00Z"),
            "1A001B"));

    assertThat(state.draft()).isEmpty();
  }

  @Test
  void givenSubmittedDraft_whenDraftRead_thenEmpty() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1,
            occurredAt,
            "1A001B",
            Erasable.of(
                new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Text", null, null, null)),
            Erasable.of("{}")));
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 0L, occurredAt));

    assertThat(state.draft()).isEmpty();
  }

  private PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, Instant uploadedAt, String originalFilename) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        uploadedAt,
        10L,
        "application/pdf",
        "sum",
        UUID.randomUUID(),
        "CIVIL_APPLY",
        "pdf",
        originalFilename);
  }

  private PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, Instant uploadedAt) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        uploadedAt,
        10L,
        "application/pdf",
        "sum",
        UUID.randomUUID(),
        "CIVIL_APPLY");
  }
}
