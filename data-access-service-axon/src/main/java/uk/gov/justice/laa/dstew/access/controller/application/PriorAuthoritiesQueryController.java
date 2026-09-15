package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthoritiesApi;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;

/** HTTP query adapter for retrieving Prior Authority requests. */
@RestController
public class PriorAuthoritiesQueryController {
  private final GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private final GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;

  /**
   * Constructor for `PriorAuthoritiesQueryController`.
   *
   * @param getPriorAuthorityUseCase Use case for retrieving Prior Authority requests
   * @param getPriorAuthorityResponseMapper Mapper for converting domain models to API responses
   */
  public PriorAuthoritiesQueryController(
      GetPriorAuthorityUseCase getPriorAuthorityUseCase,
      GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper) {
    this.getPriorAuthorityUseCase = getPriorAuthorityUseCase;
    this.getPriorAuthorityResponseMapper = getPriorAuthorityResponseMapper;
  }

  /**
   * Retrieves the Prior Authority request identified by the supplied UUID, whether it is still an
   * in-progress draft or has already been submitted.
   *
   * @param serviceName calling service identifier
   * @param priorAuthorityId identifier of the Prior Authority request
   * @return the requested Prior Authority response
   */
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @GetMapping(PriorAuthoritiesApi.PATH_GET_PRIOR_AUTHORITY)
  public ResponseEntity<PriorAuthorityResponse> getPriorAuthority(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID priorAuthorityId) {
    return ResponseEntity.ok(
        getPriorAuthorityResponseMapper.toResponse(
            getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)));
  }
}
