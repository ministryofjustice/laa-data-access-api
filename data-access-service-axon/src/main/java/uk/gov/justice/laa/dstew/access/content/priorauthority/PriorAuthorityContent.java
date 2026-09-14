package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Top-level content model for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record PriorAuthorityContent(
    PriorAuthorityType priorAuthorityType,
    String justification,
    ExpertDetails expertDetails,
    CounselDetails counselDetails,
    DisbursementDetails disbursementDetails,
    List<PriorAuthorityDocument> uploadedDocuments) {

  /** Creates content without any uploaded documents. */
  public PriorAuthorityContent(
      PriorAuthorityType priorAuthorityType,
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
        null);
  }
}
