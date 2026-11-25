package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class SubmitPriorAuthorityDraftUseCaseTest {

  private static final AuthenticatedCaller CALLER =
      new AuthenticatedCaller(Optional.empty(), Set.of(), Set.of("1A001B"), Set.of());

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private QueryGateway queryGateway;

  @InjectMocks private SubmitPriorAuthorityDraftUseCase useCase;

  @Test
  void givenCommand_whenSubmit_thenDispatchesSubmitCommand() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();

    useCase.submit(command);

    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenDraftExists_whenSubmit_thenDispatchesValidationBeforeSubmitCommand() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    when(queryGateway.query(
            new FindPriorAuthorityDraftQuery(command.priorAuthorityId()),
            PriorAuthorityDraftSummary.class))
        .thenReturn(
            CompletableFuture.completedFuture(new PriorAuthorityDraftSummary(applicationId, null)));

    useCase.submit(command);

    InOrder order = Mockito.inOrder(dispatcher);
    order.verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    order.verify(dispatcher).dispatch(command);
  }

  @Test
  void givenNoDraftExists_whenSubmit_thenThrowsNotFoundBeforeDispatch() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(queryGateway.query(
            new FindPriorAuthorityDraftQuery(command.priorAuthorityId()),
            PriorAuthorityDraftSummary.class))
        .thenReturn(CompletableFuture.completedFuture(PriorAuthorityDraftSummary.NONE));

    assertThatThrownBy(() -> useCase.submit(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + command.priorAuthorityId());
    verifyNoInteractions(dispatcher);
  }

  @Test
  void givenDraftQueryReturnsNull_whenSubmit_thenThrowsNotFoundBeforeDispatch() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(queryGateway.query(
            new FindPriorAuthorityDraftQuery(command.priorAuthorityId()),
            PriorAuthorityDraftSummary.class))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatThrownBy(() -> useCase.submit(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + command.priorAuthorityId());
    verifyNoInteractions(dispatcher);
  }

  @Test
  void givenValidationFails_whenSubmit_thenPropagatesAndSkipsSubmitCommand() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    when(queryGateway.query(
            new FindPriorAuthorityDraftQuery(command.priorAuthorityId()),
            PriorAuthorityDraftSummary.class))
        .thenReturn(
            CompletableFuture.completedFuture(new PriorAuthorityDraftSummary(applicationId, null)));
    ValidationException failure = new ValidationException(List.of("Application must be granted"));
    doThrow(failure)
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(applicationId));

    assertThatThrownBy(() -> useCase.submit(command)).isSameAs(failure);
    verify(dispatcher, never()).dispatch(command);
  }

  private SubmitPriorAuthorityDraftCommand stubCommand() {
    UUID priorAuthorityId = UUID.randomUUID();
    lenient()
        .when(
            queryGateway.query(
                new FindPriorAuthorityDraftQuery(priorAuthorityId),
                PriorAuthorityDraftSummary.class))
        .thenReturn(
            CompletableFuture.completedFuture(
                new PriorAuthorityDraftSummary(UUID.randomUUID(), null)));
    return new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now(), CALLER);
  }
}
