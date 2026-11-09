package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityProjectionTest {

  @Mock private PriorAuthorityReadRepository repository;
  @InjectMocks private PriorAuthorityProjection projection;

  @Test
  void givenDocumentEvents_whenReplayedTwice_thenProjectsMetadataOnceWithTypeAndDeletedFlag() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID keptId = UUID.randomUUID();
    UUID deletedId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-10T09:00:00Z");
    PriorAuthorityReadModel model =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .status("DRAFT")
            .build();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(model));
    var keptUpload =
        new PriorAuthorityDocumentUploadedEvent(
            priorAuthorityId,
            keptId,
            uploadedAt,
            12L,
            "application/pdf",
            "checksum",
            applicationId,
            "CIVIL_APPLY",
            ".PDF");
    var deletedUpload =
        uploaded(priorAuthorityId, deletedId, applicationId, uploadedAt.plusSeconds(1));
    var typeUpdate =
        new PriorAuthorityDocumentTypeUpdatedEvent(
            priorAuthorityId, keptId, "EXPERT_REPORT", uploadedAt.plusSeconds(2), applicationId);
    var deletion =
        new PriorAuthorityDocumentDeletedEvent(
            priorAuthorityId, deletedId, uploadedAt.plusSeconds(3), applicationId);

    for (int replay = 0; replay < 2; replay++) {
      projection.on(keptUpload);
      projection.on(deletedUpload);
      projection.on(typeUpdate);
      projection.on(deletion);
    }

    assertThat(model.getUploadedDocuments())
        .containsExactly(
            new DocumentMetadata(
                keptId,
                "EXPERT_REPORT",
                uploadedAt,
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY",
                false,
                ".PDF"),
            new DocumentMetadata(
                deletedId,
                null,
                uploadedAt.plusSeconds(1),
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY",
                true));
    assertThat(model.getModifiedAt()).isEqualTo(uploadedAt.plusSeconds(3));
  }

  private static PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, UUID applicationId, Instant uploadedAt) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        applicationId,
        "CIVIL_APPLY");
  }

  @Test
  void givenExistingDraft_whenSubmitted_thenPreservesCreatedAtAndUpdatesCurrentState() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-09-10T09:00:00Z");
    Instant submittedAt = Instant.parse("2026-09-11T10:00:00Z");
    PriorAuthorityReadModel draft =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .dataVersion(0L)
            .status("DRAFT")
            .createdAt(createdAt)
            .build();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(draft));
    when(repository.save(draft)).thenReturn(draft);

    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 1L, submittedAt));

    assertThat(draft.getStatus()).isEqualTo("SUBMITTED");
    assertThat(draft.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(draft.getDataVersion()).isEqualTo(1L);
    assertThat(draft.getCreatedAt()).isEqualTo(createdAt);
    assertThat(draft.getDecision()).isNull();
    verify(repository).save(draft);
  }

  @Test
  void givenDraftStartedEvent_whenHandled_thenCreatesCurrentStateRow() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-04T10:00:00Z");
    PriorAuthorityDraftStartedEvent event =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, occurredAt, "OFFICE-1");
    PriorAuthorityReadModel[] savedCapture = new PriorAuthorityReadModel[1];
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(event);

    assertThat(savedCapture[0].getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(savedCapture[0].getApplicationId()).isEqualTo(applicationId);
    assertThat(savedCapture[0].getDataVersion()).isZero();
    assertThat(savedCapture[0].getStatus()).isEqualTo("DRAFT");
    assertThat(savedCapture[0].getPriorAuthorityType()).isEqualTo(EXPERT.name());
    assertThat(savedCapture[0].getOfficeCode()).isEqualTo("OFFICE-1");
    assertThat(savedCapture[0].getCreatedAt()).isEqualTo(occurredAt);
    assertThat(savedCapture[0].getModifiedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenDecisionMadeEvent_whenHandled_thenUpdatesCurrentStateVersion() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityReadModel model =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .dataVersion(0L)
            .status("SUBMITTED")
            .build();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(model));

    projection.on(
        new PriorAuthorityDecisionMadeEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1L,
            "REFUSED",
            "Refused on merits",
            null,
            null,
            Instant.now()));

    assertThat(model.getStatus()).isEqualTo("DECIDED");
    assertThat(model.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(model.getDataVersion()).isEqualTo(1L);
    verify(repository).save(model);
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllInBatch() {
    projection.reset();

    verify(repository).deleteAllInBatch();
  }
}
