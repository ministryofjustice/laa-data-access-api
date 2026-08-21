package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Apportionment details when costs are shared across parties. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Apportionment(Integer partiesSharingCosts, BigDecimal clientShareAmount) {}
