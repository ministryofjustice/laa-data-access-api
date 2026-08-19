package uk.gov.justice.laa.dstew.access.query.application;

import java.time.Instant;
import java.util.UUID;

/** Non-sensitive identifiers for one Application awaiting automatic assessment. */
public record StalledAssessment(UUID applicationId, long applicationVersion, Instant submittedAt) {}
