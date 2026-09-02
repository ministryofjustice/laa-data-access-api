package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class MakeApplicationDecisionUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private AssignmentInvariantService assignmentInvariantService;
  private MakeApplicationDecisionUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    assignmentInvariantService = mock(AssignmentInvariantService.class);
    useCase = new MakeApplicationDecisionUseCase(dispatcher, assignmentInvariantService);
  }

  @Test
  void givenCommand_whenExecute_thenValidatesAssignmentBeforeDispatching() {
    MakeApplicationDecisionCommand command = stubCommand();

    useCase.execute(command);

    var ordered = inOrder(assignmentInvariantService, dispatcher);
    ordered.verify(assignmentInvariantService).validate(command);
    ordered.verify(dispatcher).dispatch(command);
  }

  private MakeApplicationDecisionCommand stubCommand() {
    return new MakeApplicationDecisionCommand(
        UUID.randomUUID(), 0L, "GRANTED", false, List.of(), null, "{}", "decision", Instant.now());
  }
}
