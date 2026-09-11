package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;

/** Sensitive data associated with one prior-authority submission. */
public record PriorAuthorityDataPayload(
    UUID priorAuthorityId,
    UUID applicationId,
    PriorAuthorityContent content,
    String serialisedRequest,
    Instant submittedAt,
    String decision,
    String decisionJustification,
    Double amountGranted,
    Instant dateGranted,
    ExpertFeeInformation expert,
    DisbursementInformation disbursement,
    ApportionmentInformation apportionment,
    String decisionSerialisedRequest) {

  /** Creates an initial payload version before any decision has been recorded. */
  public PriorAuthorityDataPayload(
      UUID priorAuthorityId,
      UUID applicationId,
      PriorAuthorityContent content,
      String serialisedRequest,
      Instant submittedAt) {
    this(
        priorAuthorityId,
        applicationId,
        content,
        serialisedRequest,
        submittedAt,
        null,
        null,
        null,
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
      ExpertFeeInformation expert,
      DisbursementInformation disbursement,
      ApportionmentInformation apportionment,
      String newDecisionSerialisedRequest) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId,
        applicationId,
        content,
        serialisedRequest,
        submittedAt,
        newDecision,
        newDecisionJustification,
        newAmountGranted,
        newDateGranted,
        expert,
        disbursement,
        apportionment,
        newDecisionSerialisedRequest);
  }
}
