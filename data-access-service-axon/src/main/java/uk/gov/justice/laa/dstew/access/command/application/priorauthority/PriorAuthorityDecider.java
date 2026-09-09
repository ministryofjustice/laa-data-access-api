package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
            command.priorAuthorityType(),
            0L,
            fingerprint,
            PriorAuthorityStatus.PENDING.name(),
            command.schemaVersion(),
            command.occurredAt()));
  }

  /**
   * Returns a decision event for a pending submission, empty for an idempotent retry, or throws on
   * an incompatible terminal state.
   */
  public static Optional<PriorAuthorityDecisionRecordedEvent> decideDecision(
      PriorAuthorityState state,
      MakePriorAuthorityDecisionCommand command,
      PriorAuthorityDataPayload current) {
    validateDecision(command);

    if (command.expectedPriorAuthorityVersion() != state.dataVersion) {
      throw new PriorAuthorityVersionConflictException(
          command.submissionId(), command.expectedPriorAuthorityVersion());
    }

    if (!PriorAuthorityStatus.PENDING.name().equals(state.status)) {
      boolean sameDecision = state.status != null && state.status.equals(command.overallDecision());
      boolean sameRequest =
          current.decisionSerialisedRequest() != null
              && current.decisionSerialisedRequest().equals(command.serialisedRequest());
      if (sameDecision && sameRequest) {
        return Optional.empty();
      }
      throw new PriorAuthorityStatusConflictException(command.submissionId(), state.status);
    }

    return Optional.of(
        new PriorAuthorityDecisionRecordedEvent(
            command.submissionId(),
            state.applicationId,
            state.priorAuthorityType,
            state.dataVersion + 1,
            command.overallDecision(),
            command.decisionJustification(),
            command.amountGranted(),
            command.dateGranted(),
            command.occurredAt()));
  }

  private static void validateDecision(MakePriorAuthorityDecisionCommand command) {
    if (!PriorAuthorityStatus.GRANTED.name().equals(command.overallDecision())
        && !PriorAuthorityStatus.REFUSED.name().equals(command.overallDecision())) {
      throw new ValidationException(List.of("overallDecision must be one of: GRANTED, REFUSED"));
    }
  }
}
