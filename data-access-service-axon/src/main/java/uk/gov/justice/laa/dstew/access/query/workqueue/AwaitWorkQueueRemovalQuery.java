package uk.gov.justice.laa.dstew.access.query.workqueue;

import java.util.UUID;

/** Internal subscription query that signals removal of an application's work queue row. */
public record AwaitWorkQueueRemovalQuery(UUID applicationId) {}

