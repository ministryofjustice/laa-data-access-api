package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class MakeApplicationDecisionUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private MakeApplicationDecisionUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new MakeApplicationDecisionUseCase(dispatcher);
  }

  @Test
  void givenCommand_whenExecute_thenDispatchesToTheApplicationAggregate() {
    MakeApplicationDecisionCommand command = stubCommand();

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private MakeApplicationDecisionCommand stubCommand() {
    return new MakeApplicationDecisionCommand(
        UUID.randomUUID(),
        UUID.randomUUID(),
        0L,
        "GRANTED",
        List.of(),
        null,
        "{}",
        "decision",
        Instant.now());
  }
}
