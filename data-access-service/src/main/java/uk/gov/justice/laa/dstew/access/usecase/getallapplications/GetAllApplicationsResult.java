package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.PagedResult;

/**
 * Use-case result record carrying the paged application summaries and validated pagination metadata
 * needed to construct the paging envelope.
 */
public record GetAllApplicationsResult(
    PagedResult<ApplicationSummaryReadModel> applications,
    int requestedPage,
    int requestedPageSize) {}
