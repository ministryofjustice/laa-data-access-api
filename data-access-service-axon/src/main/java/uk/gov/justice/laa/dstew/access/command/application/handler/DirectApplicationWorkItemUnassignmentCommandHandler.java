package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectWorkItemUnassignmentCommand;

/** Handles direct unassignment of application work items. */
@Component
public class DirectApplicationWorkItemUnassignmentCommandHandler {

  /** Unassigns an active application work item. */
  @CommandHandler
  public void handle(
      DirectWorkItemUnassignmentCommand command,
      @InjectEntity(idProperty = "workItemId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.validateDirectWorkItem(
        application, command.workItemId(), command.expectedAssignmentVersion());
    if (application.getState().getCaseworkerId() == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }
    long nextAssignmentVersion = application.getState().getAssignmentVersion() + 1;
    eventAppender.append(
        new WorkItemUnassigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            application.getState().getApplicationVersion(),
            nextAssignmentVersion,
            command.occurredAt()));
  }
}
