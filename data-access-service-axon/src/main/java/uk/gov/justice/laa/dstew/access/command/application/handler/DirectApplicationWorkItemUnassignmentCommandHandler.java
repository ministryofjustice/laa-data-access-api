package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
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
    var state = application.getState();
    ApplicationDecider.validateUnassignable(
        state, command.workItemId(), command.expectedAssignmentVersion());
    eventAppender.append(ApplicationDecider.decideUnassign(state, command.occurredAt()));
  }
}
