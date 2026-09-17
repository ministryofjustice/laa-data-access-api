package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;

/** Verifies the Prior Authority decision endpoint delegates to the write use case. */
@ExtendWith(MockitoExtension.class)
class PriorAuthoritiesCommandControllerTest {

  @Mock private MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase;
  @Mock private MakePriorAuthorityDecisionCommandMapper makePriorAuthorityDecisionCommandMapper;

  @InjectMocks private PriorAuthoritiesCommandController controller;

  @Test
  void givenDecisionRequest_whenMakePriorAuthorityDecision_thenDelegatesAndReturnsNoContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    MakePriorAuthorityDecisionRequest request = mock(MakePriorAuthorityDecisionRequest.class);
    MakePriorAuthorityDecisionCommand command = mock(MakePriorAuthorityDecisionCommand.class);
    when(makePriorAuthorityDecisionCommandMapper.toCommand(priorAuthorityId, request))
        .thenReturn(command);

    ResponseEntity<Void> actual =
        controller.makePriorAuthorityDecision(null, priorAuthorityId, request);

    assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(makePriorAuthorityDecisionUseCase).execute(command);
  }
}
