package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class UnassignCaseworkerUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private UnassignCaseworkerUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new UnassignCaseworkerUseCase(dispatcher);
  }

  @Test
  void givenCommand_whenExecute_thenDelegatesToRetryingDispatcher() {
    UnassignCaseworkerFromApplicationCommand command = stubCommand();

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private UnassignCaseworkerFromApplicationCommand stubCommand() {
    return new UnassignCaseworkerFromApplicationCommand(
        UUID.randomUUID(), "{}", "unassign", Instant.now());
  }
}
