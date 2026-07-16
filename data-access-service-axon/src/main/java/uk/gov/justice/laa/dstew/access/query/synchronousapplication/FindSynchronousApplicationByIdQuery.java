package uk.gov.justice.laa.dstew.access.query.synchronousapplication;

import java.util.UUID;

/** Query to retrieve a SynchronousApplication by its Apply Application identifier. */
public record FindSynchronousApplicationByIdQuery(UUID applyApplicationId) {}
