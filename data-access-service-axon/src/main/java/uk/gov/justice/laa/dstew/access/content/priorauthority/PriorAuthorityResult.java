package uk.gov.justice.laa.dstew.access.content.priorauthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/** Typed result of retrieving a prior-authority submission. */
@Builder
public record PriorAuthorityResult(
    UUID priorAuthorityId,
    UUID applicationId,
    String justification,
    String status,
    Instant submittedAt,
    PriorAuthorityType priorAuthorityType,
    ExpertDetails expertDetails,
    CounselDetails counselDetails,
    DisbursementDetails disbursementDetails,
    List<DocumentMetadata> uploadedDocuments,
    Map<UUID, String> documentFilenames,
    PriorAuthorityDataPayload.DecisionDetails decisionDetails) {

  /**
   * Builds the use-case result from the current-state projection and the referenced content, which
   * may be a partial draft when the status is {@code DRAFT}.
   */
  public static PriorAuthorityResult from(
      PriorAuthorityReadModel priorAuthority, PriorAuthorityDataPayload payload) {
    PriorAuthorityContent content = payload.content();
    PriorAuthorityType priorAuthorityType = content.priorAuthorityType();
    return new PriorAuthorityResult(
        priorAuthority.getPriorAuthorityId(),
        priorAuthority.getApplicationId(),
        content.justification(),
        priorAuthority.getStatus(),
        PriorAuthorityStatus.DRAFT.name().equals(priorAuthority.getStatus())
            ? null
            : payload.submittedAt(),
        priorAuthorityType,
        priorAuthorityType == PriorAuthorityType.EXPERT ? toExpertDetails(content) : null,
        priorAuthorityType == PriorAuthorityType.COUNSEL ? toCounselDetails(content) : null,
        priorAuthorityType == PriorAuthorityType.DISBURSEMENT
            ? toDisbursementDetails(content)
            : null,
        priorAuthority.getUploadedDocuments(),
        payload.documentFilenames(),
        payload.decisionDetails());
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
