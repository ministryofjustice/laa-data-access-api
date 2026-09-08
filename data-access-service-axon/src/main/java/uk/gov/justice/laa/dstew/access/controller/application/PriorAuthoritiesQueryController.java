package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthoritiesApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;

/** HTTP query adapter for retrieving Prior Authority requests. */
@RestController
public class PriorAuthoritiesQueryController implements PriorAuthoritiesApi {
  private final GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private final GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;
  private final MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase;
  private final MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper;

  /** Creates the query and decision controller for prior-authority endpoints. */
  public PriorAuthoritiesQueryController(
      GetPriorAuthorityUseCase getPriorAuthorityUseCase,
      GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper,
      MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase,
      MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper) {
    this.getPriorAuthorityUseCase = getPriorAuthorityUseCase;
    this.getPriorAuthorityResponseMapper = getPriorAuthorityResponseMapper;
    this.makePriorAuthorityDecisionUseCase = makePriorAuthorityDecisionUseCase;
    this.makePriorAuthorityDecisionCommandMapper = makePriorAuthorityDecisionCommandMapper;
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

  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  public ResponseEntity<Void> makePriorAuthorityDecision(
      ServiceName serviceName,
      UUID priorAuthorityId,
      MakePriorAuthorityDecisionRequest makePriorAuthorityDecisionRequest) {
    makePriorAuthorityDecisionUseCase.execute(
        makePriorAuthorityDecisionCommandMapper.toCommand(
            priorAuthorityId, makePriorAuthorityDecisionRequest));
    return ResponseEntity.noContent().build();
  }
}
