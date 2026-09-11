package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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

  /**
   * Returns a decision event for a submitted prior-authority, empty for an idempotent retry, or
   * throws on a stale version or invalid lifecycle state.
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

    if (!PriorAuthorityStatus.SUBMITTED.name().equals(state.status)) {
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
    if (!DecisionValue.GRANTED.name().equals(command.overallDecision())
        && !DecisionValue.REFUSED.name().equals(command.overallDecision())) {
      throw new ValidationException(List.of("overallDecision must be one of: GRANTED, REFUSED"));
    }
  }
}
