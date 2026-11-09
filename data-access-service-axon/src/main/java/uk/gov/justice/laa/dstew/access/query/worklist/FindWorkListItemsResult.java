package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;

/**
 * One server-paged result from the replayable active-work projection, with the application
 * summaries of the page's subject applications. A summary is absent while it lags.
 */
public record FindWorkListItemsResult(
    List<WorkListItemReadModel> items,
    Map<UUID, ApplicationSummaryReadModel> applicationSummaries,
    long totalElements,
    int requestedPage,
    int requestedPageSize) {}
