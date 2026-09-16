package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** HTTP command adapter for Prior Authority decision writes. */
@RestController
public class PriorAuthoritiesCommandController implements PriorAuthorityCommandApi {
  private final MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase;
  private final MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper;

  /** Creates the command adapter. */
  public PriorAuthoritiesCommandController(
      MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase,
      MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper) {
    this.makePriorAuthorityDecisionUseCase = makePriorAuthorityDecisionUseCase;
    this.makePriorAuthorityDecisionCommandMapper = makePriorAuthorityDecisionCommandMapper;
  }

  /** Records a decision against an existing Prior Authority request and returns 204. */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @PatchMapping(PriorAuthorityCommandApi.PATH_MAKE_PRIOR_AUTHORITY_DECISION)
  public ResponseEntity<Void> makePriorAuthorityDecision(
      @NotNull @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID priorAuthorityId,
      @Valid @RequestBody MakePriorAuthorityDecisionRequest makePriorAuthorityDecisionRequest) {
    makePriorAuthorityDecisionUseCase.execute(
        makePriorAuthorityDecisionCommandMapper.toCommand(
            priorAuthorityId, makePriorAuthorityDecisionRequest));
    return ResponseEntity.noContent().build();
  }
}
