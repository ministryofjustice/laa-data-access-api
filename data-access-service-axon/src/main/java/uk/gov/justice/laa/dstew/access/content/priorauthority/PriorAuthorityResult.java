package uk.gov.justice.laa.dstew.access.content.priorauthority;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/** Typed result of retrieving a prior-authority submission. */
public record PriorAuthorityResult(
    UUID priorAuthorityId,
    UUID applicationId,
    String justification,
    String status,
    String decision,
    String decisionJustification,
    PriorAuthorityType priorAuthorityType,
    ExpertDetails expertDetails,
    CounselDetails counselDetails,
    DisbursementDetails disbursementDetails) {

  /** Backward-compatible constructor when decision values are absent. */
  public PriorAuthorityResult(
      UUID priorAuthorityId,
      UUID applicationId,
      String justification,
      String status,
      PriorAuthorityType priorAuthorityType,
      ExpertDetails expertDetails,
      CounselDetails counselDetails,
      DisbursementDetails disbursementDetails) {
    this(
        priorAuthorityId,
        applicationId,
        justification,
        status,
        null,
        null,
        priorAuthorityType,
        expertDetails,
        counselDetails,
        disbursementDetails);
  }

  /** Builds the use-case result from the current-state projection and versioned content. */
  public static PriorAuthorityResult from(
      PriorAuthorityReadModel priorAuthority, PriorAuthorityDataPayload payload) {
    return build(
        priorAuthority.getPriorAuthorityId(),
        priorAuthority.getApplicationId(),
        priorAuthority.getStatus(),
        payload.content(),
        payload.decision(),
        payload.decisionJustification());
  }

  /**
   * Builds the use-case result for an in-progress draft, whose content may be partial since it has
   * not yet been schema-validated.
   */
  public static PriorAuthorityResult fromDraft(PriorAuthorityDataPayload payload) {
    return build(
        payload.priorAuthorityId(),
        payload.applicationId(),
        PriorAuthorityStatus.DRAFT.name(),
        payload.content(),
        payload.decision(),
        payload.decisionJustification());
  }

  private static PriorAuthorityResult build(
      UUID priorAuthorityId,
      UUID applicationId,
      String status,
      PriorAuthorityContent content,
      String decision,
      String decisionJustification) {
    PriorAuthorityType priorAuthorityType = content.priorAuthorityType();
    return new PriorAuthorityResult(
        priorAuthorityId,
        applicationId,
        content.justification(),
        status,
        decision,
        decisionJustification,
        priorAuthorityType,
        priorAuthorityType == PriorAuthorityType.EXPERT ? toExpertDetails(content) : null,
        priorAuthorityType == PriorAuthorityType.COUNSEL ? toCounselDetails(content) : null,
        priorAuthorityType == PriorAuthorityType.DISBURSEMENT
            ? toDisbursementDetails(content)
            : null);
  }

  private static ExpertDetails toExpertDetails(PriorAuthorityContent content) {
    if (content.expertDetails() == null) {
      return null;
    }
    var expertCosts = content.expertDetails().expertCosts();
    return new ExpertDetails(
        content.expertDetails().expertType(),
        content.expertDetails().expertFullName(),
        content.expertDetails().expertPostcode(),
        expertCosts);
  }

  private static CounselDetails toCounselDetails(PriorAuthorityContent content) {
    return content.counselDetails() == null
        ? null
        : new CounselDetails(content.counselDetails().counselType());
  }

  private static DisbursementDetails toDisbursementDetails(PriorAuthorityContent content) {
    return content.disbursementDetails() == null ? null : content.disbursementDetails();
  }
}
