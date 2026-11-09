package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.document.DocumentFileSuffix;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Decision functions: derive events from current state and command inputs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityDecider {

  /** Returns a {@link PriorAuthorityDraftStartedEvent} for the first save of a new draft. */
  public static PriorAuthorityDraftStartedEvent decideStartDraft(
      CreatePriorAuthorityDraftCommand command, String officeCode) {
    String priorAuthorityType =
        command.content().priorAuthorityType() == null
            ? null
            : command.content().priorAuthorityType().name();
    return new PriorAuthorityDraftStartedEvent(
        command.priorAuthorityId(),
        command.applicationId(),
        priorAuthorityType,
        command.schemaVersion(),
        command.occurredAt(),
        officeCode,
        Erasable.of(command.content()),
        Erasable.of(command.serialisedRequest()));
  }

  /**
   * Returns a {@link PriorAuthoritySubmittedEvent} for the given submit command. The event carries
   * no content; the state seals the draft content as the submission.
   */
  public static PriorAuthoritySubmittedEvent decideSubmit(
      SubmitPriorAuthorityDraftCommand command, PriorAuthorityState state) {
    return new PriorAuthoritySubmittedEvent(
        command.priorAuthorityId(),
        state.applicationId,
        state.priorAuthorityType,
        state.schemaVersion,
        state.expertType(),
        command.occurredAt());
  }

  /**
   * Returns a {@link PriorAuthorityDraftUpdatedEvent} carrying the replacement draft content. The
   * prior-authority type is fixed when the draft starts, so it overrides any type in the update.
   */
  public static PriorAuthorityDraftUpdatedEvent decideDraftUpdated(
      UpdatePriorAuthorityDraftCommand command, PriorAuthorityState state) {
    PriorAuthorityContent content =
        state.priorAuthorityType == null
            ? command.content()
            : command
                .content()
                .withPriorAuthorityType(PriorAuthorityType.valueOf(state.priorAuthorityType));
    return new PriorAuthorityDraftUpdatedEvent(
        command.priorAuthorityId(),
        state.applicationId,
        command.occurredAt(),
        Erasable.of(content),
        Erasable.of(command.serialisedRequest()));
  }

  /**
   * Returns a decision event for a submitted prior-authority or throws on a stale version or
   * invalid lifecycle state.
   */
  public static Optional<PriorAuthorityDecisionMadeEvent> decideDecision(
      PriorAuthorityState state, MakePriorAuthorityDecisionCommand command) {
    validateDecision(command);
    validateDecisionAssignment(state, command);

    if (command.expectedPriorAuthorityVersion() != state.priorAuthorityVersion) {
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
        command.overallDecision(),
        command.decisionJustification(),
        command.amountGranted(),
        command.dateGranted(),
        command.expertFee(),
        command.disbursementInformation(),
        command.apportionmentInformation(),
        command.occurredAt());
  }

  private static PriorAuthorityStatus statusOf(PriorAuthorityState state) {
    if (state.decided) {
      return PriorAuthorityStatus.DECIDED;
    }
    if (!state.submitted) {
      return PriorAuthorityStatus.DRAFT;
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
        applicationId,
        command.sourceService(),
        DocumentFileSuffix.fromFilename(command.originalFilename()),
        command.originalFilename());
  }

  /** Returns the persisted event for removing a prior-authority document. */
  public static PriorAuthorityDocumentDeletedEvent decideDocumentDeleted(
      PriorAuthorityDocumentDeleteCommand command, UUID applicationId) {
    return new PriorAuthorityDocumentDeletedEvent(
        command.priorAuthorityId(), command.documentId(), command.occurredAt(), applicationId);
  }

  /** Returns the persisted event for setting or replacing a document type. */
  public static PriorAuthorityDocumentTypeUpdatedEvent decideDocumentTypeUpdated(
      PriorAuthorityDocumentTypeUpdateCommand command, UUID applicationId) {
    return new PriorAuthorityDocumentTypeUpdatedEvent(
        command.priorAuthorityId(),
        command.documentId(),
        command.documentType(),
        command.occurredAt(),
        applicationId);
  }
}
