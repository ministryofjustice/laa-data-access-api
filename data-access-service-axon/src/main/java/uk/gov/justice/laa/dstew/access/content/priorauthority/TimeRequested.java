package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Time requested for an expert in a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@ExcludeFromGeneratedCodeCoverage
public record TimeRequested(Integer hours, Integer minutes) {}
