package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

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
class UpdatePriorAuthorityDraftUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private UpdatePriorAuthorityDraftUseCase useCase;

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
