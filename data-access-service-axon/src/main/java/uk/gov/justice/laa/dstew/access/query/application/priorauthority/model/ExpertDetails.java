package uk.gov.justice.laa.dstew.access.query.application.priorauthority.model;

/** Expert details returned by the get-prior-authority use case. */
public record ExpertDetails(
    String expertType, String expertFullName, String expertPostcode, ExpertCosts expertCosts) {}
