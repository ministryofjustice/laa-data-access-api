package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class SubmitPriorAuthorityDraftUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private QueryGateway queryGateway;

  @InjectMocks private SubmitPriorAuthorityDraftUseCase useCase;

  @Test
  void givenDraftExists_whenSubmit_thenValidatesGrantedBeforeDispatchingSubmit() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    stubDraft(command.priorAuthorityId(), new PriorAuthorityDraftSummary(applicationId, Map.of()));

    useCase.submit(command);

    InOrder order = Mockito.inOrder(dispatcher);
    order.verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    order.verify(dispatcher).dispatch(command);
  }

  @Test
  void givenNoDraftExists_whenSubmit_thenThrowsNotFoundBeforeDispatch() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    stubDraft(command.priorAuthorityId(), PriorAuthorityDraftSummary.NONE);

    assertThatThrownBy(() -> useCase.submit(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + command.priorAuthorityId());
    verifyNoInteractions(dispatcher);
  }

  @Test
  void givenValidationFails_whenSubmit_thenPropagatesWithoutSubmitting() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    stubDraft(command.priorAuthorityId(), new PriorAuthorityDraftSummary(applicationId, Map.of()));
    ValidationException failure = new ValidationException(List.of("Application must be granted"));
    doThrow(failure)
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(applicationId));

    assertThatThrownBy(() -> useCase.submit(command)).isSameAs(failure);
    verify(dispatcher, never()).dispatch(command);
  }

  private SubmitPriorAuthorityDraftCommand stubCommand() {
    return new SubmitPriorAuthorityDraftCommand(UUID.randomUUID(), Instant.now());
  }

  private void stubDraft(UUID priorAuthorityId, PriorAuthorityDraftSummary draft) {
    when(queryGateway.query(
            new FindPriorAuthorityDraftQuery(priorAuthorityId), PriorAuthorityDraftSummary.class))
        .thenReturn(CompletableFuture.completedFuture(draft));
  }
}
