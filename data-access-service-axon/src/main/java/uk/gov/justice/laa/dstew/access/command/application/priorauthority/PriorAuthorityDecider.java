package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
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
   *
   * @param applicationDataVersion the parent application's current data version, pinned into the
   *     event so the work-list projection hydrates parent fields deterministically on replay
   */
  public static PriorAuthoritySubmittedEvent decideSubmit(
      SubmitPriorAuthorityDraftCommand command,
      PriorAuthorityState state,
      long applicationDataVersion) {
    return new PriorAuthoritySubmittedEvent(
        command.priorAuthorityId(),
        state.applicationId,
        state.priorAuthorityType,
        state.schemaVersion,
        0L,
        applicationDataVersion,
        command.occurredAt());
  }

  /**
   * Returns a {@link PriorAuthorityDraftUpdatedEvent} — a thin, PII-free pointer with no draft
   * content — for a draft-body update. It exists to seal the write through the aggregate's
   * optimistic-concurrency gate.
   */
  public static PriorAuthorityDraftUpdatedEvent decideDraftUpdated(
      UpdatePriorAuthorityDraftCommand command, UUID applicationId) {
    return new PriorAuthorityDraftUpdatedEvent(
        command.priorAuthorityId(), applicationId, command.occurredAt());
  }

  /**
   * Returns a decision event for a submitted prior-authority or throws on a stale version or
   * invalid lifecycle state.
   */
  public static Optional<PriorAuthorityDecisionMadeEvent> decideDecision(
      PriorAuthorityState state,
      MakePriorAuthorityDecisionCommand command,
      PriorAuthorityDataPayload current) {
    validateDecision(command);
    validateDecisionAssignment(state, command);

    if (command.expectedPriorAuthorityVersion() != state.dataVersion) {
      throw new PriorAuthorityVersionConflictException(
          command.priorAuthorityId(), command.expectedPriorAuthorityVersion());
    }

    return switch (statusOf(state)) {
      case DRAFT ->
          throw new PriorAuthorityStatusConflictException(
              command.priorAuthorityId(),
              "Prior authority must be submitted before a decision can be made");
      case DECIDED ->
          throw new PriorAuthorityStatusConflictException(
              command.priorAuthorityId(), "Prior authority has already been decided");
      case SUBMITTED -> Optional.of(createDecisionMadeEvent(state, command));
    };
  }

  private static PriorAuthorityDecisionMadeEvent createDecisionMadeEvent(
      PriorAuthorityState state, MakePriorAuthorityDecisionCommand command) {
    return new PriorAuthorityDecisionMadeEvent(
        command.priorAuthorityId(),
        state.applicationId,
        state.priorAuthorityType,
        state.dataVersion + 1,
        command.overallDecision(),
        command.decisionJustification(),
        command.amountGranted(),
        command.dateGranted(),
        command.occurredAt());
  }

  private static PriorAuthorityStatus statusOf(PriorAuthorityState state) {
    if (!state.submitted) {
      return PriorAuthorityStatus.DRAFT;
    }
    if (state.overallDecision != null) {
      return PriorAuthorityStatus.DECIDED;
    }
    return PriorAuthorityStatus.SUBMITTED;
  }

  private static void validateDecision(MakePriorAuthorityDecisionCommand command) {
    if (!DecisionValue.GRANTED.name().equals(command.overallDecision())
        && !DecisionValue.REFUSED.name().equals(command.overallDecision())) {
      throw new ValidationException(List.of("overallDecision must be one of: GRANTED, REFUSED"));
    }
  }

  /** Ensures a prior-authority decision is made by its current assignment owner. */
  private static void validateDecisionAssignment(
      PriorAuthorityState state, MakePriorAuthorityDecisionCommand command) {
    if (state.caseworkerId == null) {
      throw new PriorAuthorityStatusConflictException(
          command.priorAuthorityId(), "Prior authority is unassigned and cannot be decided");
    }
    if (!state.caseworkerId.equals(command.caseworkerId())) {
      throw new PriorAuthorityStatusConflictException(
          command.priorAuthorityId(),
          "Prior authority is assigned to a different caseworker and cannot be decided by this user");
    }
  }

  /** Returns a persisted upload event for a prior-authority document finalize command. */
  public static PriorAuthorityDocumentUploadedEvent decideDocumentUploaded(
      PriorAuthorityDocumentUploadCommand command, UUID applicationId) {
    return new PriorAuthorityDocumentUploadedEvent(
        command.priorAuthorityId(),
        command.documentId(),
        command.occurredAt(),
        command.fileSize(),
        command.contentType(),
        command.checksum(),
        applicationId);
  }

  /** Returns the persisted event for setting or replacing a document type. */
  public static PriorAuthorityDocumentTypeUpdatedEvent decideDocumentTypeUpdated(
      PriorAuthorityDocumentTypeUpdateCommand command) {
    return new PriorAuthorityDocumentTypeUpdatedEvent(
        command.priorAuthorityId(),
        command.documentId(),
        command.documentType(),
        command.occurredAt());
  }
}
