package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocumentMetadata;

/** Decision functions: derive events from current state and command inputs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityDecider {

  /** Returns a {@link PriorAuthorityDraftStartedEvent} for the first save of a new draft. */
  public static PriorAuthorityDraftStartedEvent decideStartDraft(
      CreatePriorAuthorityDraftCommand command) {
    String priorAuthorityType =
        command.content().priorAuthorityType() == null
            ? null
            : command.content().priorAuthorityType().name();
    return new PriorAuthorityDraftStartedEvent(
        command.priorAuthorityId(),
        command.applicationId(),
        priorAuthorityType,
        command.schemaVersion(),
        command.occurredAt());
  }

  /**
   * Returns a {@link PriorAuthoritySubmittedEvent} — a thin pointer with no personal data — for the
   * given submit command. The submitted content is always appended as version 0 of {@code
   * prior_authority_data}, since a submission's draft content is not itself versioned.
   */
  public static PriorAuthoritySubmittedEvent decideSubmit(
      SubmitPriorAuthorityDraftCommand command, PriorAuthorityState state) {
    return new PriorAuthoritySubmittedEvent(
        command.priorAuthorityId(),
        state.applicationId,
        state.priorAuthorityType,
        state.schemaVersion,
        0L,
        command.occurredAt());
  }

  /** Returns a persisted upload event for a prior-authority document finalize command. */
  public static PriorAuthorityDocumentUploadedEvent decideDocumentUploaded(
      PriorAuthorityDocumentUploadCommand command, UUID applicationId) {
    return new PriorAuthorityDocumentUploadedEvent(
        command.priorAuthorityId(),
        command.documentId(),
        command.occurredAt(),
        command.fileSize(),
        PriorAuthorityDocumentMetadata.PDF_CONTENT_TYPE,
        command.checksum(),
        applicationId);
  }
}
