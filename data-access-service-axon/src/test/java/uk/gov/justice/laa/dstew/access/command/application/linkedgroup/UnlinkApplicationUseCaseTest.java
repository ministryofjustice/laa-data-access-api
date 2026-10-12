package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnlinkApplicationUseCaseTest {

  @Mock private UnlinkApplicationCommandHandler commandHandler;

  @InjectMocks private UnlinkApplicationUseCase useCase;

  @Test
  void givenCommand_whenExecuted_thenDelegatesToCommandHandler() {
    var command =
        new UnlinkApplicationCommand(
            UUID.randomUUID(),
            new ExpectedLinkedGroup(UUID.randomUUID(), 3),
            Instant.parse("2026-09-14T15:00:00Z"));

    useCase.execute(command);

    verify(commandHandler).handle(command);
    verifyNoMoreInteractions(commandHandler);
  }
}
