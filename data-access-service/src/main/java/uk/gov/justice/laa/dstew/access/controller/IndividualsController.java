package uk.gov.justice.laa.dstew.access.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.IndividualsApi;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.Paging;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * REST controller for managing individuals.
 * Implements the OpenAPI-generated {@link IndividualsApi} interface.
 */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class IndividualsController implements IndividualsApi {

  private final IndividualsService individualsService;

  /**
   * Retrieves a paginated list of individuals.
   *
   * @param serviceName the service name header
   * @param include the additional data to be included in response
   * @param page the page number (1-based), may be null for default
   * @param pageSize the number of items per page, may be null for default
   * @param applicationId the application UUID to filter by (nullable)
   * @param type the individual type to filter by (nullable)
   * @return a {@link ResponseEntity} containing the {@link IndividualsResponse} with paging info
   */
  @Override
  @LogMethodResponse
  @LogMethodArguments
  @AllowApiCaseworker
  public ResponseEntity<IndividualsResponse> getIndividuals(
      ServiceName serviceName,
      IncludedAdditionalData include,
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType type
  ) {
    validateRequest(applicationId, include);

    PaginationHelper.PaginatedResult<Individual> result =
        individualsService.getIndividuals(page, pageSize, applicationId, type, include);

    List<Individual> individuals = result.page().stream().toList();
    Paging paging = new Paging();
    paging.setPage(result.requestedPage());
    paging.pageSize(result.requestedPageSize());
    paging.totalRecords((int) result.page().getTotalElements());
    paging.itemsReturned(individuals.size());

    IndividualsResponse response = new IndividualsResponse();
    response.setIndividuals(individuals);
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  private void validateRequest(UUID applicationId, IncludedAdditionalData includedDataTypes) {
    if (includedDataTypes == null) {
      return;
    }

    if (includedDataTypes == IncludedAdditionalData.CLIENT_DETAILS) {
      if (applicationId == null) {
        throw new ValidationException(List.of("Application ID is required when included data is CLIENT_DETAILS"));
      }
    }
  }
}
