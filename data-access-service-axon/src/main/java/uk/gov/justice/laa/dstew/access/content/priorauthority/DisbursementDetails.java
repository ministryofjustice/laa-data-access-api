package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Disbursement details for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisbursementDetails(String disbursementPurpose, BigDecimal disbursementAmount) {}
