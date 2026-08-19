package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Time requested for an expert in a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeRequested(Integer hours, Integer minutes) {}
