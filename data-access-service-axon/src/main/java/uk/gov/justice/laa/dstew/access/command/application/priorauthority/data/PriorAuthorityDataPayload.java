package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.With;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;

/** Sensitive data associated with one prior-authority submission. */
@With
public record PriorAuthorityDataPayload(
    UUID priorAuthorityId,
    UUID applicationId,
    PriorAuthorityContent content,
    String serialisedRequest,
    Instant submittedAt,
    DecisionDetails decisionDetails) {

  /** Decision data recorded once a prior-authority request has been decided. */
  public record DecisionDetails(
      String decision,
      String decisionJustification,
      BigDecimal amountGranted,
      Instant dateGranted,
      ExpertFeeInformation expert,
      DisbursementInformation disbursement,
      ApportionmentInformation apportionment,
      String decisionSerialisedRequest) {}

  /** Creates an initial payload version before any decision has been recorded. */
  public PriorAuthorityDataPayload(
      UUID priorAuthorityId,
      UUID applicationId,
      PriorAuthorityContent content,
      String serialisedRequest,
      Instant submittedAt) {
    this(priorAuthorityId, applicationId, content, serialisedRequest, submittedAt, null);
  }

  /** Returns a complete new data version containing the supplied decision details. */
  public PriorAuthorityDataPayload withDecision(DecisionDetails newDecision) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId, applicationId, content, serialisedRequest, submittedAt, newDecision);
  }

  /** Returns the recorded decision value, or {@code null} if the request has not been decided. */
  public String decision() {
    return decisionDetails == null ? null : decisionDetails.decision();
  }

  /** Returns the recorded decision justification, or {@code null} if undecided. */
  public String decisionJustification() {
    return decisionDetails == null ? null : decisionDetails.decisionJustification();
  }

  /** Returns the amount granted, or {@code null} if undecided. */
  public BigDecimal amountGranted() {
    return decisionDetails == null ? null : decisionDetails.amountGranted();
  }

  /** Returns the date granted, or {@code null} if undecided. */
  public Instant dateGranted() {
    return decisionDetails == null ? null : decisionDetails.dateGranted();
  }

  /** Returns the expert fee details, or {@code null} if undecided. */
  public ExpertFeeInformation expert() {
    return decisionDetails == null ? null : decisionDetails.expert();
  }

  /** Returns the disbursement details, or {@code null} if undecided. */
  public DisbursementInformation disbursement() {
    return decisionDetails == null ? null : decisionDetails.disbursement();
  }

  /** Returns the apportionment details, or {@code null} if undecided. */
  public ApportionmentInformation apportionment() {
    return decisionDetails == null ? null : decisionDetails.apportionment();
  }

  /** Returns the serialised decision request, or {@code null} if undecided. */
  public String decisionSerialisedRequest() {
    return decisionDetails == null ? null : decisionDetails.decisionSerialisedRequest();
  }
}
