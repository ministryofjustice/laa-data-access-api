package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Apportionment details when costs are shared across parties. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record Apportionment(Integer partiesSharingCosts, BigDecimal clientShareAmount) {}
