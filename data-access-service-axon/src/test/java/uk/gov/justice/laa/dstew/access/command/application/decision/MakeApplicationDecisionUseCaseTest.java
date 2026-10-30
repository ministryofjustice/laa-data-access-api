package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MakeApplicationDecisionUseCaseTest {

  private MakeApplicationDecisionCoordinator coordinator;
  private MakeApplicationDecisionUseCase useCase;

  @BeforeEach
  void setUp() {
    coordinator = mock(MakeApplicationDecisionCoordinator.class);
    useCase = new MakeApplicationDecisionUseCase(coordinator);
  }

  @Test
  void givenCommand_whenExecuted_thenDelegatesToCoordinator() {
    MakeApplicationDecisionCommand command = stubCommand();

    useCase.execute(command);

    verify(coordinator).handle(command);
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
        Instant.now(),
        null);
  }
}
