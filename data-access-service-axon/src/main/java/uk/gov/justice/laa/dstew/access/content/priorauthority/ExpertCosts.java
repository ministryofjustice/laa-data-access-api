package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Expert costs breakdown for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record ExpertCosts(
    String billingType,
    BigDecimal hourlyRate,
    TimeRequested timeRequested,
    BigDecimal totalAmount,
    Boolean costsSharedWithOtherParties,
    Apportionment apportionment) {}
