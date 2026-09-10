package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;

class MakePriorAuthorityDecisionUseCaseTest {

  @Test
  void givenDecisionCommand_whenExecute_thenValidatesGrantedApplicationThenDispatchesCommand() {
    RetryingCommandDispatcher dispatcher = mock(RetryingCommandDispatcher.class);
    QueryGateway queryGateway = mock(QueryGateway.class);
    MakePriorAuthorityDecisionUseCase useCase =
        new MakePriorAuthorityDecisionUseCase(dispatcher, queryGateway);
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId,
            0L,
            "GRANTED",
            "Decision recorded",
            100.0,
            null,
            Instant.now(),
            "{}",
            Instant.now());
    when(queryGateway.query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(submissionId),
            PriorAuthorityResult.class))
        .thenReturn(
            CompletableFuture.completedFuture(
                new PriorAuthorityResult(
                    submissionId,
                    applicationId,
                    "Need expert",
                    "SUBMITTED",
                    PriorAuthorityType.EXPERT,
                    null,
                    null,
                    null)));

    useCase.execute(command);

    var inOrder = inOrder(dispatcher);
    inOrder.verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    inOrder.verify(dispatcher).dispatch(command);
  }

  @Test
  void givenMissingPriorAuthority_whenExecute_thenThrowsNotFoundAndDoesNotDispatch() {
    RetryingCommandDispatcher dispatcher = mock(RetryingCommandDispatcher.class);
    QueryGateway queryGateway = mock(QueryGateway.class);
    MakePriorAuthorityDecisionUseCase useCase =
        new MakePriorAuthorityDecisionUseCase(dispatcher, queryGateway);
    UUID submissionId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId,
            0L,
            "GRANTED",
            "Decision recorded",
            100.0,
            null,
            Instant.now(),
            "{}",
            Instant.now());
    when(queryGateway.query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(submissionId),
            PriorAuthorityResult.class))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessage("No prior authority found with ID: " + submissionId);

    verifyNoInteractions(dispatcher);
  }
}
