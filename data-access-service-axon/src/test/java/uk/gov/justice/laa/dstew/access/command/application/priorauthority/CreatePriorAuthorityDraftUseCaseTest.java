package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreatePriorAuthorityDraftUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private CreatePriorAuthorityDraftUseCase useCase;

  @Test
  void givenCommand_whenExecute_thenDispatchesCommandDirectlyAndReturnsTrue() {
    CreatePriorAuthorityDraftCommand command = stubCommand();

    boolean result = useCase.execute(command);

    assertThat(result).isTrue();
    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenCommand_whenExecute_thenDispatchesValidationBeforeSaveDraftCommand() {
    CreatePriorAuthorityDraftCommand command = stubCommand();

    useCase.execute(command);

    InOrder order = Mockito.inOrder(dispatcher);
    order
        .verify(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
    order.verify(dispatcher).dispatch(command);
  }

  @Test
  void givenValidationFails_whenExecute_thenPropagatesAndSkipsSaveDraftDispatch() {
    CreatePriorAuthorityDraftCommand command = stubCommand();
    ValidationException failure = new ValidationException(List.of("Application must be granted"));
    doThrow(failure)
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));

    assertThatThrownBy(() -> useCase.execute(command)).isSameAs(failure);
    verify(dispatcher, never()).dispatch(command);
  }

  private static CreatePriorAuthorityDraftCommand stubCommand() {
    return new CreatePriorAuthorityDraftCommand(
        UUID.randomUUID(), UUID.randomUUID(), null, "{}", 1, "PriorAuthority.json", Instant.now());
  }
}
