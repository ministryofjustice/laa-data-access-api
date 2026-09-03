package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;

/** Decision functions: derive events from current state and command inputs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityDecider {

  /**
   * Returns a singleton {@link PriorAuthorityCreatedEvent} for a new submission, {@link
   * Optional#empty()} for an idempotent retry with the same fingerprint, or throws {@link
   * PriorAuthorityCreationConflictException} on a conflicting retry.
   */
  public static Optional<PriorAuthorityCreatedEvent> decideCreate(
      PriorAuthorityState state, CreatePriorAuthorityCommand command, String fingerprint) {

    if (state.submissionId != null) {
      if (state.requestFingerprint.equals(fingerprint)) {
        return Optional.empty();
      }
      throw new PriorAuthorityCreationConflictException(command.submissionId());
    }

    return Optional.of(
        new PriorAuthorityCreatedEvent(
            command.submissionId(),
            command.applicationId(),
            0L,
            fingerprint,
            PriorAuthorityStatus.PENDING.name(),
            command.schemaVersion(),
            command.occurredAt()));
  }

  /**
   * Returns a {@link PriorAuthorityDraftStartedEvent} for the first save of a new draft, using the
   * supplied fingerprint and resolved application ID.
   */
  public static PriorAuthorityDraftStartedEvent decideStartDraft(
      CreatePriorAuthorityDraftCommand command, String fingerprint, UUID applicationId) {
    return new PriorAuthorityDraftStartedEvent(
        command.submissionId(),
        applicationId,
        fingerprint,
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
        command.submissionId(),
        state.applicationId,
        0L,
        PriorAuthorityStatus.PENDING.name(),
        command.occurredAt());
  }
}
