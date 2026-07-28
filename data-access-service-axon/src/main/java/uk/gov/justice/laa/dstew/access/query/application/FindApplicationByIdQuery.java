package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Finds an Application's current-state projection by its internal identifier. */
public record FindApplicationByIdQuery(UUID applicationId) {}
