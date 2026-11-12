package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.List;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignedWorkItems;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;

/** Handles direct assignment of application work items. */
@Component
public class DirectApplicationWorkItemAssignmentCommandHandler {

  /**
   * Assigns an active application work item and returns it with its LAA reference, read from the
   * immutable application data at the version the state currently points at.
   */
  @CommandHandler
  public AssignedWorkItems handle(
      DirectWorkItemAssignmentCommand command,
      @InjectEntity(idProperty = "workItemId") ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    var state = application.getState();
    ApplicationDecider.validateAssignable(
        state, command.workItemId(), command.expectedAssignmentVersion());
    eventAppender.append(
        ApplicationDecider.decideAssign(state, command.caseworkerId(), command.occurredAt()));
    String laaReference =
        applicationDataStore
            .get(command.workItemId(), state.getApplicationDataVersion())
            .laaReference();
    return new AssignedWorkItems(
        List.of(new AssignedWorkItems.AssignedItem(command.workItemId(), laaReference)));
  }
}
