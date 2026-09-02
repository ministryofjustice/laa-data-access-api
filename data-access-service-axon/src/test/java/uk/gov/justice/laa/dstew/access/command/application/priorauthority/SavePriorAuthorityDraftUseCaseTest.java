package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class SavePriorAuthorityDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private SavePriorAuthorityDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new SavePriorAuthorityDraftUseCase(dispatcher);
  }

  @Test
  void givenCommand_whenCreate_thenDispatchesCommandDirectlyAndReturnsTrue() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    boolean result = useCase.create(command);

    assertThat(result).isTrue();
    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenCommand_whenUpdate_thenDispatchesCommandDirectly() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    useCase.update(command);

    verify(dispatcher).dispatch(command);
  }

  private static SavePriorAuthorityDraftCommand stubCommand() {
    return new SavePriorAuthorityDraftCommand(
        UUID.randomUUID(), UUID.randomUUID(), null, "{}", 1, "PriorAuthority.json", Instant.now());
  }
}
