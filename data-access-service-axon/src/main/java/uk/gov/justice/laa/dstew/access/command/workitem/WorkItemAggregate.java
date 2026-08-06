package uk.gov.justice.laa.dstew.access.command.workitem;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.exception.NotAssignedCaseworkerException;

/** Event-sourced consistency boundary for a WorkItem and its assignment state. */
@EventSourced(tagKey = "WorkItemAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class WorkItemAggregate {

  private UUID workItemId;
  private final WorkItemState state = new WorkItemState();

  @CommandHandler
  void handle(AssignWorkItemCommand command, EventAppender eventAppender) {
    eventAppender.append(WorkItemDecider.decideAssign(state, command));
  }

  @CommandHandler
  void handle(UnassignWorkItemCommand command, EventAppender eventAppender) {
    if (state.caseworkerId == null) {
      return;
    }
    eventAppender.append(WorkItemDecider.decideUnassign(state, command));
  }

  /** Proves that the requested caseworker is assigned to this existing work item. */
  @CommandHandler
  void handle(ValidateWorkItemAssignmentCommand command) {
    if (workItemId == null || !command.caseworkerId().equals(state.caseworkerId)) {
      throw new NotAssignedCaseworkerException(command.applicationId());
    }
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    WorkItemEvolve.apply(state, event);
    this.workItemId = state.workItemId;
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    WorkItemEvolve.apply(state, event);
  }

  @EntityCreator
  protected WorkItemAggregate() {}

  /** Returns the UUID of the currently assigned caseworker, or {@code null} if unassigned. */
  public UUID caseworkerId() {
    return state.caseworkerId;
  }
}
