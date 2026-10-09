package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;

/** Handles direct assignment of application work items. */
@Component
public class DirectApplicationWorkItemAssignmentCommandHandler {

  /** Assigns an active application work item. */
  @CommandHandler
  public void handle(
      DirectWorkItemAssignmentCommand command,
      @InjectEntity(idProperty = "workItemId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.validateDirectWorkItem(
        application, command.workItemId(), command.expectedAssignmentVersion());
    if (application.getState().getCaseworkerId() != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }
    long nextAssignmentVersion = application.getState().getAssignmentVersion() + 1;
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            application.getState().getApplicationVersion(),
            nextAssignmentVersion,
            command.caseworkerId(),
            command.occurredAt()));
  }
}
