package uk.gov.justice.laa.dstew.access.query.application;

import java.time.Instant;

/** Finds assessment-eligible Applications submitted before an operational threshold. */
public record FindStalledAssessmentsQuery(Instant submittedBefore) {}
