package uk.gov.justice.laa.dstew.access.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.IndividualsApi;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
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
  public ResponseEntity<IndividualsResponse> getIndividuals(Integer page, Integer pageSize) {
    return ResponseEntity.ok(individualsService.getIndividualsResponse(page, pageSize));
  }
}
