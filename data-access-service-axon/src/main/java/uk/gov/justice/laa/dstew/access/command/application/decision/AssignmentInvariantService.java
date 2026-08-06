package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.Metadata;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.workitem.ValidateWorkItemAssignmentCommand;


/**
 * Guards the make-decision flow behind the WorkItem assignment invariant.
 *
 * <p>Handles {@link MakeApplicationDecisionCommand} from the command bus, verifies the requesting
 * caseworker through the WorkItem aggregate, then dispatches {@link ExecuteApplicationDecisionCommand}
 * to the Application aggregate.
 */
@Service
public class AssignmentInvariantService {

  private final CommandGateway commandGateway;

  /** Creates the invariant guard. */
  public AssignmentInvariantService(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Validates the assignment state before dispatching the application decision command. */
  @CommandHandler
  public CompletableFuture<Void> handle(MakeApplicationDecisionCommand command) {
    UUID workItemId = WorkItemId.toAggregateId(command.applicationId());

    return commandGateway
        .send(
            new ValidateWorkItemAssignmentCommand(
                workItemId, command.applicationId(), command.caseworkerId()),
            Metadata.emptyInstance(),
            null)
        .getResultMessage()
        .thenCompose(
            ignored ->
                commandGateway
                    .send(ExecuteApplicationDecisionCommand.from(command), Metadata.emptyInstance(), null)
                    .getResultMessage())
        .thenApply(ignored -> null);
  }
}

