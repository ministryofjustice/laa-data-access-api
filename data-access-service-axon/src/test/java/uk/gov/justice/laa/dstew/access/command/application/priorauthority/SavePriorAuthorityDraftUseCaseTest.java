package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
  void givenCommand_whenCreate_thenDispatchesValidationBeforeSaveDraftCommand() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    useCase.create(command);

    InOrder order = Mockito.inOrder(dispatcher);
    order
        .verify(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
    order.verify(dispatcher).dispatch(command);
  }

  @Test
  void givenValidationFails_whenCreate_thenPropagatesAndSkipsSaveDraftDispatch() {
    SavePriorAuthorityDraftCommand command = stubCommand();
    ValidationException failure = new ValidationException(List.of("Application must be granted"));
    doThrow(failure)
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));

    assertThatThrownBy(() -> useCase.create(command)).isSameAs(failure);
    verify(dispatcher, never()).dispatch(command);
  }

  @Test
  void givenCommand_whenUpdate_thenDispatchesCommandDirectly() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    useCase.update(command);

    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenCommand_whenUpdate_thenNeverValidatesApplication() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    useCase.update(command);

    verify(dispatcher, never())
        .dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
  }

  private static SavePriorAuthorityDraftCommand stubCommand() {
    return new SavePriorAuthorityDraftCommand(
        UUID.randomUUID(), UUID.randomUUID(), null, "{}", 1, "PriorAuthority.json", Instant.now());
  }
}
