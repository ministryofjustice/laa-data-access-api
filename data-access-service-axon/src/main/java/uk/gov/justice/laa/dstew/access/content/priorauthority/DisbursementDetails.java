package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Disbursement details for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record DisbursementDetails(String disbursementPurpose, BigDecimal disbursementAmount) {}
