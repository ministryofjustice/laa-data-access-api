package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityBySubmissionIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

class SubmitPriorAuthorityDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private SubscriptionProjectionGateway projectionGateway;
  private SubmitPriorAuthorityDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    projectionGateway = mock(SubscriptionProjectionGateway.class);
    useCase = new SubmitPriorAuthorityDraftUseCase(dispatcher, projectionGateway);
  }

  @Test
  void givenCommand_whenProjectionConfirmed_thenReturnsTrue() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(PriorAuthorityReadModel.class), any()))
        .thenReturn(true);

    boolean result = useCase.submit(command);

    assertThat(result).isTrue();
  }

  @Test
  void givenCommand_whenProjectionTimeout_thenReturnsFalse() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(PriorAuthorityReadModel.class), any()))
        .thenReturn(false);

    boolean result = useCase.submit(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenCommand_whenSubmit_thenPassesExactQueryAndModelClass() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(PriorAuthorityReadModel.class), any()))
        .thenReturn(true);

    useCase.submit(command);

    verify(projectionGateway)
        .awaitProjection(
            eq(new FindPriorAuthorityBySubmissionIdQuery(command.submissionId())),
            eq(PriorAuthorityReadModel.class),
            any());
  }

  @Test
  void givenCommand_whenSubmit_thenSupplierDispatchesSubmitCommand() {
    SubmitPriorAuthorityDraftCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(PriorAuthorityReadModel.class), any());

    useCase.submit(command);

    verify(dispatcher).dispatch(command);
  }

  private SubmitPriorAuthorityDraftCommand stubCommand() {
    return new SubmitPriorAuthorityDraftCommand(UUID.randomUUID(), Instant.now());
  }
}
