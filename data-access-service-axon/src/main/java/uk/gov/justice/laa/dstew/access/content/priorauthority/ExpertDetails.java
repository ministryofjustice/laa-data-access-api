package uk.gov.justice.laa.dstew.access.content.priorauthority;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Expert details for a prior-authority application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpertDetails(
    String expertType, String expertFullName, String expertPostcode, ExpertCosts expertCosts) {}
