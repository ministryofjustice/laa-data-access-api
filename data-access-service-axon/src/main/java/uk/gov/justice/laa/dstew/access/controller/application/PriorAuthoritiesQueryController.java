package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthoritiesApi;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;

/** HTTP query adapter for retrieving Prior Authority requests. */
@RestController
public class PriorAuthoritiesQueryController implements PriorAuthoritiesApi {
  private final GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private final GetPriorAuthorityDraftUseCase getPriorAuthorityDraftUseCase;
  private final GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;

  /**
   * Constructor for `PriorAuthoritiesQueryController`.
   *
   * @param getPriorAuthorityUseCase Use case for retrieving Prior Authority requests
   * @param getPriorAuthorityDraftUseCase Use case for retrieving Prior Authority drafts
   * @param getPriorAuthorityResponseMapper Mapper for converting domain models to API responses
   */
  public PriorAuthoritiesQueryController(
      GetPriorAuthorityUseCase getPriorAuthorityUseCase,
      GetPriorAuthorityDraftUseCase getPriorAuthorityDraftUseCase,
      GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper) {
    this.getPriorAuthorityUseCase = getPriorAuthorityUseCase;
    this.getPriorAuthorityDraftUseCase = getPriorAuthorityDraftUseCase;
    this.getPriorAuthorityResponseMapper = getPriorAuthorityResponseMapper;
  }

  /**
   * Retrieves the Prior Authority request identified by the supplied UUID.
   *
   * @param serviceName calling service identifier
   * @param priorAuthorityId identifier of the Prior Authority request
   * @return the requested Prior Authority response
   */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  public ResponseEntity<PriorAuthorityResponse> getPriorAuthority(
      ServiceName serviceName, UUID priorAuthorityId) {
    return ResponseEntity.ok(
        getPriorAuthorityResponseMapper.toResponse(
            getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)));
  }

  /**
   * Retrieves the current draft content for an in-progress Prior Authority submission.
   *
   * @param serviceName calling service identifier
   * @param priorAuthorityId identifier of the Prior Authority draft
   * @return the requested Prior Authority draft response
   */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  public ResponseEntity<PriorAuthorityResponse> getPriorAuthorityDraft(
      ServiceName serviceName, UUID priorAuthorityId) {
    return ResponseEntity.ok(
        getPriorAuthorityResponseMapper.toResponse(
            getPriorAuthorityDraftUseCase.getPriorAuthorityDraft(priorAuthorityId)));
  }
}
