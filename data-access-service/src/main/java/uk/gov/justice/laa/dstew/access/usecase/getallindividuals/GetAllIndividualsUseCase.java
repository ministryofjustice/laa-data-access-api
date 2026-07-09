package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePage;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePageSize;

import java.util.List;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsIndividualGateway;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Orchestrates retrieval of a paginated, filtered list of individuals. */
@RequiredArgsConstructor
public class GetAllIndividualsUseCase {

  private final GetAllIndividualsIndividualGateway individualGateway;
  private final GetAllIndividualsApplicationGateway applicationGateway;

  /**
   * Retrieves a page of individuals, optionally enriched with CLIENT_DETAILS.
   *
   * @param query the query record carrying all filter and pagination parameters
   * @return result record containing paged individuals and optional client details
   */
  @AllowApiCaseworker
  public GetAllIndividualsResult execute(GetAllIndividualsQuery query) {
    if ("CLIENT_DETAILS".equals(query.include()) && query.applicationId() == null) {
      throw new ValidationException(
          List.of("Application ID is required when included data is CLIENT_DETAILS"));
    }

    int resolvedPage = validatePage(query.page());
    int resolvedPageSize = validatePageSize(query.pageSize());

    PagedResult<IndividualDomain> individuals =
        individualGateway.findAll(
            query.applicationId(), query.individualType(), resolvedPage, resolvedPageSize);

    ApplicationClientDetailsDomain clientDetails = null;
    if ("CLIENT".equals(query.individualType()) && query.include() != null) {
      clientDetails = applicationGateway.findClientDetails(query.applicationId());
    }

    return GetAllIndividualsResult.builder()
        .individuals(individuals)
        .requestedPage(resolvedPage)
        .requestedPageSize(resolvedPageSize)
        .clientDetails(clientDetails)
        .build();
  }
}
