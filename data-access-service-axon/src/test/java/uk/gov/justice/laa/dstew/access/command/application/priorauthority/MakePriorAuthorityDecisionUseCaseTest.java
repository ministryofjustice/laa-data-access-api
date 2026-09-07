package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class MakePriorAuthorityDecisionUseCaseTest {

  @Test
  void givenDecisionCommand_whenExecute_thenDispatchesCommand() {
    RetryingCommandDispatcher dispatcher = mock(RetryingCommandDispatcher.class);
    MakePriorAuthorityDecisionUseCase useCase = new MakePriorAuthorityDecisionUseCase(dispatcher);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            UUID.randomUUID(), "GRANTED", "{}", "Decision recorded", Instant.now());

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }
}
