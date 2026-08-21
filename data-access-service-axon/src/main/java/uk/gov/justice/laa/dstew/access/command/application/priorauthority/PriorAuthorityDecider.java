package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.Optional;
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
}
