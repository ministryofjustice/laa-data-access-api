package uk.gov.justice.laa.dstew.access.query.workqueue;

import java.util.List;

/** Result returned by {@link FindWorkQueueItemsQuery}. */
public record FindWorkQueueItemsResult(
    List<WorkQueueReadModel> items,
    long totalElements,
    int requestedPage,
    int requestedPageSize) {}
