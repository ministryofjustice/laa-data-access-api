package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;

/** Query to retrieve a Application by its Apply Application identifier. */
public record FindApplicationByIdQuery(UUID applyApplicationId) {}
