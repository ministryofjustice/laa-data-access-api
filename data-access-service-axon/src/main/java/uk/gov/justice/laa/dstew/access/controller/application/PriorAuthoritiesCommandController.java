package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

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
  @LogMethodArguments
  @LogMethodResponse
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
