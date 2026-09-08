package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;

/** Sensitive data associated with one prior-authority submission. */
public record PriorAuthorityDataPayload(
    UUID submissionId,
    UUID applicationId,
    PriorAuthorityContent content,
    String serialisedRequest,
    Instant submittedAt,
    String decision,
    String decisionJustification,
    Double amountGranted,
    Instant dateGranted,
    String decisionSerialisedRequest) {

  /** Creates an initial payload version before any decision has been recorded. */
  public PriorAuthorityDataPayload(
      UUID submissionId,
      UUID applicationId,
      PriorAuthorityContent content,
      String serialisedRequest,
      Instant submittedAt) {
    this(
        submissionId,
        applicationId,
        content,
        serialisedRequest,
        submittedAt,
        null,
        null,
        null,
        null,
        null);
  }

  /** Returns a complete new data version containing the supplied decision details. */
  public PriorAuthorityDataPayload withDecision(
      String newDecision,
      String newDecisionJustification,
      Double newAmountGranted,
      Instant newDateGranted,
      String newDecisionSerialisedRequest) {
    return new PriorAuthorityDataPayload(
        submissionId,
        applicationId,
        content,
        serialisedRequest,
        submittedAt,
        newDecision,
        newDecisionJustification,
        newAmountGranted,
        newDateGranted,
        newDecisionSerialisedRequest);
  }
}
