package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.PagedResultDomain;

/**
 * Use-case result record carrying the paged application summaries and validated pagination metadata
 * needed to construct the paging envelope.
 */
public record GetAllApplicationsResult(
    PagedResultDomain<ApplicationSummaryDomain> applications,
    int requestedPage,
    int requestedPageSize) {}
