package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthoritySubmittedByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class SubmitPriorAuthorityDraftUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SubscriptionProjectionGateway projectionGateway;
  @Mock private PriorAuthorityDraftStore draftStore;

  @InjectMocks private SubmitPriorAuthorityDraftUseCase useCase;

  @Test
  void givenCommand_whenProjectionConfirmed_thenReturnsTrue() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(true);

    boolean result = useCase.submit(command);

    assertThat(result).isTrue();
  }

  @Test
  void givenCommand_whenProjectionTimeout_thenReturnsFalse() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(false);

    boolean result = useCase.submit(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenCommand_whenSubmit_thenPassesExactPendingQuery() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(true);

    useCase.submit(command);

    verify(projectionGateway)
        .awaitProjection(
            eq(new PriorAuthoritySubmittedByPriorAuthorityIdQuery(command.priorAuthorityId())),
            any());
  }

  @Test
  void givenCommand_whenSubmit_thenSupplierDispatchesSubmitCommand() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(1);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), any());

    useCase.submit(command);

    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenDraftExists_whenSubmit_thenDispatchesValidationBeforeProjectionGateway() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    when(draftStore.find(command.priorAuthorityId()))
        .thenReturn(Optional.of(stubDraftPayload(command.priorAuthorityId(), applicationId)));
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(true);

    useCase.submit(command);

    InOrder order = Mockito.inOrder(dispatcher, projectionGateway);
    order.verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    order.verify(projectionGateway).awaitProjection(any(), any());
  }

  @Test
  void givenNoDraftExists_whenSubmit_thenThrowsNotFoundBeforeDispatch() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(draftStore.find(command.priorAuthorityId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.submit(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + command.priorAuthorityId());
    verifyNoInteractions(dispatcher, projectionGateway);
  }

  @Test
  void givenValidationFails_whenSubmit_thenPropagatesAndSkipsProjectionGateway() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    UUID applicationId = UUID.randomUUID();
    when(draftStore.find(command.priorAuthorityId()))
        .thenReturn(Optional.of(stubDraftPayload(command.priorAuthorityId(), applicationId)));
    ValidationException failure = new ValidationException(List.of("Application must be granted"));
    doThrow(failure)
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(applicationId));

    assertThatThrownBy(() -> useCase.submit(command)).isSameAs(failure);
    verify(projectionGateway, never()).awaitProjection(any(), any());
  }

  private SubmitPriorAuthorityDraftCommand stubCommand() {
    UUID priorAuthorityId = UUID.randomUUID();
    lenient()
        .when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(stubDraftPayload(priorAuthorityId, UUID.randomUUID())));
    return new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now());
  }

  private static PriorAuthorityDataPayload stubDraftPayload(
      UUID priorAuthorityId, UUID applicationId) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId, applicationId, null, "{}", Instant.now());
  }
}
