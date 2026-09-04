package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Top-level content model for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record PriorAuthorityContent(
    String priorAuthorityType,
    String justification,
    ExpertDetails expertDetails,
    CounselDetails counselDetails,
    DisbursementDetails disbursementDetails,
    List<PriorAuthorityDocument> documents) {

  /**
   * Creates a content model with no attached documents.
   *
   * @param priorAuthorityType the type of prior authority
   * @param justification the justification for the request
   * @param expertDetails the expert details, when applicable
   * @param counselDetails the counsel details, when applicable
   * @param disbursementDetails the disbursement details, when applicable
   */
  public PriorAuthorityContent(
      String priorAuthorityType,
      String justification,
      ExpertDetails expertDetails,
      CounselDetails counselDetails,
      DisbursementDetails disbursementDetails) {
    this(
        priorAuthorityType,
        justification,
        expertDetails,
        counselDetails,
        disbursementDetails,
        List.of());
  }
}
