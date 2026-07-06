package uk.gov.justice.laa.dstew.access.usecase.getallapplications;

import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;

/**
 * Use-case result record carrying the paged application summaries and validated pagination metadata
 * needed to construct the paging envelope.
 */
public record GetAllApplicationsResult(
    Page<ApplicationSummaryDomain> applications, int requestedPage, int requestedPageSize) {}
