package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

class MakePriorAuthorityDecisionUseCaseTest {

  @Test
  void givenDecisionCommand_whenExecute_thenValidatesGrantedApplicationThenDispatchesCommand() {
    RetryingCommandDispatcher dispatcher = mock(RetryingCommandDispatcher.class);
    QueryGateway queryGateway = mock(QueryGateway.class);
    MakePriorAuthorityDecisionUseCase useCase =
        new MakePriorAuthorityDecisionUseCase(dispatcher, queryGateway);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            Instant.now(),
            "{}",
            Instant.now());
    when(queryGateway.query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            PriorAuthorityResult.class))
        .thenReturn(
            CompletableFuture.completedFuture(
                new PriorAuthorityResult(
                    priorAuthorityId,
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
    UUID priorAuthorityId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            Instant.now(),
            "{}",
            Instant.now());
    when(queryGateway.query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            PriorAuthorityResult.class))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(command))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);

    verifyNoInteractions(dispatcher);
  }
}
