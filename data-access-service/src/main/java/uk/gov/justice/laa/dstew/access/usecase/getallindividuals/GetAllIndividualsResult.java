package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import lombok.Builder;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;

/** Result record returned by the get-all-individuals use case. */
@Builder(toBuilder = true)
public record GetAllIndividualsResult(
    PagedResult<IndividualDomain> individuals,
    int requestedPage,
    int requestedPageSize,
    @Nullable ApplicationClientDetailsDomain clientDetails) {}
