package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;

class PriorAuthoritiesQueryControllerTest {

  private GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;
  private MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase;
  private MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper;
  private PriorAuthoritiesQueryController controller;

  @BeforeEach
  void setUp() {
    getPriorAuthorityUseCase = mock(GetPriorAuthorityUseCase.class);
    getPriorAuthorityResponseMapper = mock(GetPriorAuthorityResponseMapper.class);
    makePriorAuthorityDecisionUseCase = mock(MakePriorAuthorityDecisionUseCase.class);
    makePriorAuthorityDecisionCommandMapper = mock(MakePriorAuthorityDecisionCommandMapper.class);
    controller =
        new PriorAuthoritiesQueryController(
            getPriorAuthorityUseCase,
            getPriorAuthorityResponseMapper,
            makePriorAuthorityDecisionUseCase,
            makePriorAuthorityDecisionCommandMapper);
  }

  @Test
  void givenDecisionRequest_whenMakePriorAuthorityDecision_thenDelegatesAndReturnsNoContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    var request =
        mock(uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest.class);
    MakePriorAuthorityDecisionCommand command = mock(MakePriorAuthorityDecisionCommand.class);
    when(makePriorAuthorityDecisionCommandMapper.toCommand(priorAuthorityId, request))
        .thenReturn(command);

    var response = controller.makePriorAuthorityDecision(null, priorAuthorityId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(makePriorAuthorityDecisionUseCase).execute(command);
  }

  @Test
  void givenPriorAuthorityId_whenGetPriorAuthority_thenDelegatesToUseCaseAndMapper() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityResult result =
        new PriorAuthorityResult(
            priorAuthorityId,
            UUID.randomUUID(),
            "Need expert",
            "PENDING",
            null,
            null,
            null,
            null,
            null,
            null);
    var responseModel = new uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(getPriorAuthorityResponseMapper.toResponse(result)).thenReturn(responseModel);

    var response = controller.getPriorAuthority(null, priorAuthorityId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(responseModel);
  }
}
