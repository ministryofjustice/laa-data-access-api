package uk.gov.justice.laa.dstew.access.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.IndividualsApi;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.Paging;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;


/**
 * REST controller for managing individuals.
 * Implements the OpenAPI-generated {@link IndividualsApi} interface.
 */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class IndividualsController implements IndividualsApi {

  private final IndividualsService individualsService;
  private final HttpServletRequest request;

  /**
   * Retrieves a paginated list of individuals.
   *
   * @param page the page number (1-based), may be null for default
   * @param pageSize the number of items per page, may be null for default
   * @return a {@link ResponseEntity} containing the {@link IndividualsResponse} with paging info
   */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public ResponseEntity<IndividualsResponse> getIndividuals(
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType type
  ) {
    validateIndividualTypeParameter(type);

    int validatedPage = (page == null || page < 1) ? 1 : page;
    int validatedPageSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

    Page<Individual> individualsPage = individualsService.getIndividuals(
        validatedPage - 1,
        validatedPageSize,
        applicationId,
        type
    );

    List<Individual> individuals = individualsPage.getContent();

    Paging paging = new Paging();
    paging.setPage(validatedPage);
    paging.setPageSize(validatedPageSize);
    paging.setTotalRecords((int) individualsPage.getTotalElements());
    paging.setItemsReturned(individuals.size());

    IndividualsResponse response = new IndividualsResponse();
    response.setIndividuals(individuals);
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  /**
   * Validates the individualType query parameter.
   * Spring silently converts invalid enum values to null for optional parameters,
   * so we check the raw parameter to detect invalid values.
   */
  private void validateIndividualTypeParameter(IndividualType type) {
    String rawTypeParam = request.getParameter("individualType");
    if (rawTypeParam != null && type == null) {
      throw new ResponseStatusException(BAD_REQUEST, "Invalid individualType value: " + rawTypeParam);
    }
  }
}
