package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePage;
import static uk.gov.justice.laa.dstew.access.utils.PaginationHelper.validatePageSize;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
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
   * @param page one-based page number (null defaults to 1)
   * @param pageSize page size (null defaults to 20)
   * @param applicationId optional filter by application UUID
   * @param individualType optional filter by individual type String (e.g. "CLIENT")
   * @param include optional included additional data type String (e.g. "CLIENT_DETAILS")
   * @return result record containing paged individuals and optional client details
   */
  @AllowApiCaseworker
  public GetAllIndividualsResult execute(
      @Nullable Integer page,
      @Nullable Integer pageSize,
      @Nullable UUID applicationId,
      @Nullable String individualType,
      @Nullable String include) {
    if ("CLIENT_DETAILS".equals(include) && applicationId == null) {
      throw new ValidationException(
          List.of("Application ID is required when included data is CLIENT_DETAILS"));
    }

    int resolvedPage = validatePage(page);
    int resolvedPageSize = validatePageSize(pageSize);

    Page<IndividualDomain> individuals =
        individualGateway.findAll(applicationId, individualType, resolvedPage, resolvedPageSize);

    ApplicationClientDetailsDomain clientDetails = null;
    if ("CLIENT".equals(individualType) && include != null) {
      clientDetails = applicationGateway.findClientDetails(applicationId);
    }

    return GetAllIndividualsResult.builder()
        .individuals(individuals)
        .requestedPage(resolvedPage)
        .requestedPageSize(resolvedPageSize)
        .clientDetails(clientDetails)
        .build();
  }
}
