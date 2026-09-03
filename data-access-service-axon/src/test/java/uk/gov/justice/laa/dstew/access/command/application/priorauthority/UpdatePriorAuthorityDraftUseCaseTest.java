package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class UpdatePriorAuthorityDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private UpdatePriorAuthorityDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new UpdatePriorAuthorityDraftUseCase(dispatcher);
  }

  @Test
  void givenCommand_whenExecute_thenDispatchesCommandDirectly() {
    UpdatePriorAuthorityDraftCommand command = stubCommand();

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private static UpdatePriorAuthorityDraftCommand stubCommand() {
    return new UpdatePriorAuthorityDraftCommand(
        UUID.randomUUID(), null, "{}", 1, "PriorAuthority.json", Instant.now());
  }
}
