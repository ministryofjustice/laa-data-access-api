package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Checks whether an Application projection exists without requiring submitted content. */
public record ApplicationProjectionExistsQuery(UUID applicationId) {}
