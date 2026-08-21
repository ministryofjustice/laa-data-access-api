package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Top-level content model for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PriorAuthorityContent(
    String priorAuthorityType,
    String justification,
    ExpertDetails expertDetails,
    CounselDetails counselDetails,
    DisbursementDetails disbursementDetails) {}
