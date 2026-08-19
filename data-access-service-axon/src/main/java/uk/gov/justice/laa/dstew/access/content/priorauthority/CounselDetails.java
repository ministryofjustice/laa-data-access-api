package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Counsel details for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CounselDetails(String counselType) {}
