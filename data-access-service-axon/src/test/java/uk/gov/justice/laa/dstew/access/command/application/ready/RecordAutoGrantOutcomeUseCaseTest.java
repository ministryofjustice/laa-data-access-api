package uk.gov.justice.laa.dstew.access.command.application.ready;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

@ExtendWith(MockitoExtension.class)
class RecordAutoGrantOutcomeUseCaseTest {
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private MarkApplicationReadyCoordinator readyCoordinator;
  @InjectMocks private RecordAutoGrantOutcomeUseCase useCase;

  @Test
  void givenReadyCommand_whenRecorded_thenDelegatesToCoordinator() {
    MarkApplicationReadyCommand command =
        new MarkApplicationReadyCommand(UUID.randomUUID(), "{}", Instant.now());

    useCase.recordReady(command);

    verify(readyCoordinator).handle(command);
  }

  @Test
  void givenOtherOutcome_whenRecorded_thenDispatchedDirectly() {
    Object command = new Object();

    useCase.record(command);

    verify(dispatcher).dispatch(command);
  }
}
