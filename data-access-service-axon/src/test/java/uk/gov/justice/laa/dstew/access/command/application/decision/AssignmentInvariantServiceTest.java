package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.workitem.ValidateWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.NotAssignedCaseworkerException;

class AssignmentInvariantServiceTest {

  private CommandGateway commandGateway;
  private AssignmentInvariantService service;

  @BeforeEach
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    service = new AssignmentInvariantService(commandGateway);
  }

  @Test
  void givenAssignedCaseworker_whenMakingDecision_thenValidatesAndDispatchesDecision() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakeApplicationDecisionCommand command = command(applicationId, caseworkerId);

    CommandResult validationResult = successfulResult();
    CommandResult decisionResult = successfulResult();
    ValidateWorkItemAssignmentCommand validationCommand =
        new ValidateWorkItemAssignmentCommand(
            WorkItemId.toAggregateId(applicationId), applicationId, caseworkerId);
    doReturn(validationResult)
        .when(commandGateway)
        .send(validationCommand, Metadata.emptyInstance(), null);
    doReturn(decisionResult)
        .when(commandGateway)
        .send(ExecuteApplicationDecisionCommand.from(command), Metadata.emptyInstance(), null);

    CompletableFuture<Void> handled = service.handle(command);

    handled.join();

    var commands = inOrder(commandGateway);
    commands.verify(commandGateway).send(validationCommand, Metadata.emptyInstance(), null);
    commands
        .verify(commandGateway)
        .send(ExecuteApplicationDecisionCommand.from(command), Metadata.emptyInstance(), null);
  }

  @Test
  void givenDecisionCommandFailure_whenMakingDecision_thenPropagatesTheFailure() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakeApplicationDecisionCommand command = command(applicationId, caseworkerId);

    ValidateWorkItemAssignmentCommand validationCommand =
        new ValidateWorkItemAssignmentCommand(
            WorkItemId.toAggregateId(applicationId), applicationId, caseworkerId);
    doReturn(successfulResult())
        .when(commandGateway)
        .send(validationCommand, Metadata.emptyInstance(), null);

    CommandResult decisionResult = mock(CommandResult.class);
    RuntimeException failure = new IllegalStateException("Decision rejected");
    doReturn(decisionResult)
        .when(commandGateway)
        .send(ExecuteApplicationDecisionCommand.from(command), Metadata.emptyInstance(), null);
    doReturn(CompletableFuture.failedFuture(failure)).when(decisionResult).getResultMessage();

    assertThatThrownBy(() -> service.handle(command).join())
        .isInstanceOf(CompletionException.class)
        .hasCause(failure);
  }

  @Test
  void givenAssignmentValidationFailure_whenMakingDecision_thenPropagatesNotAssigned() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakeApplicationDecisionCommand command = command(applicationId, caseworkerId);
    ValidateWorkItemAssignmentCommand validationCommand =
        new ValidateWorkItemAssignmentCommand(
            WorkItemId.toAggregateId(applicationId), applicationId, caseworkerId);
    CommandResult validationResult = mock(CommandResult.class);
    NotAssignedCaseworkerException failure = new NotAssignedCaseworkerException(applicationId);
    doReturn(validationResult)
        .when(commandGateway)
        .send(validationCommand, Metadata.emptyInstance(), null);
    doReturn(CompletableFuture.failedFuture(failure)).when(validationResult).getResultMessage();

    assertThatThrownBy(() -> service.handle(command).join())
        .isInstanceOf(CompletionException.class)
        .hasCause(failure);
    verify(commandGateway, never())
        .send(ExecuteApplicationDecisionCommand.from(command), Metadata.emptyInstance(), null);
  }

  private CommandResult successfulResult() {
    CommandResult result = mock(CommandResult.class);
    Message resultMessage = mock(Message.class);
    doReturn(CompletableFuture.completedFuture(resultMessage)).when(result).getResultMessage();
    return result;
  }

  private MakeApplicationDecisionCommand command(UUID applicationId, UUID caseworkerId) {
    return new MakeApplicationDecisionCommand(
        applicationId,
        0L,
        "REFUSED",
        false,
        List.of(),
        null,
        "{}",
        null,
        Instant.now(),
        caseworkerId);
  }
}

