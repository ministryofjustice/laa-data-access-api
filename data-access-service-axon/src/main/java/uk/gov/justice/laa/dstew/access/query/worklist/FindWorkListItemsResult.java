package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.List;

/** One server-paged result from the replayable active-work projection. */
public record FindWorkListItemsResult(
    List<WorkListItemReadModel> items,
    long totalElements,
    int requestedPage,
    int requestedPageSize) {}
