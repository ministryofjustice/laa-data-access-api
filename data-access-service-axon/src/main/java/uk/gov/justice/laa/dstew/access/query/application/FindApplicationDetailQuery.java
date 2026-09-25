package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Finds an Application's hydrated detail and related read models by its internal identifier. */
public record FindApplicationDetailQuery(UUID applicationId) {}
