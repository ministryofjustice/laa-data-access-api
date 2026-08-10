package uk.gov.justice.laa.dstew.access.command.application.note;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class CreateNoteUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private CreateNoteUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new CreateNoteUseCase(dispatcher);
  }

  @Test
  void givenCommand_whenExecute_thenDelegatesToRetryingDispatcher() {
    CreateNoteCommand command = stubCommand();

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private CreateNoteCommand stubCommand() {
    return new CreateNoteCommand(UUID.randomUUID(), "note text", "{}", Instant.now());
  }
}
