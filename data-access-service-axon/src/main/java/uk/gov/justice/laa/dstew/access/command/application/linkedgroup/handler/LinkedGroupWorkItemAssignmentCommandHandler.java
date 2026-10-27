package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMembers;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

/** Handles group assignment and unassignment by appending all member events together. */
@Component
public class LinkedGroupWorkItemAssignmentCommandHandler {

  /** Assigns every active member of a linked group in one append. */
  @CommandHandler
  public void handle(
      AssignLinkedGroupWorkItemCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var state = group == null ? new LinkedApplicationGroupState() : group.getState();
    var members = LinkedGroupMembers.load(stateManager, memberIdsOrEmpty(state), context);
    eventAppender.append(LinkedGroupAssignmentDecider.decideGroupAssign(state, members, command));
  }

  /** Unassigns every assigned active member of a linked group in one append. */
  @CommandHandler
  public void handle(
      UnassignLinkedGroupWorkItemCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var state = group == null ? new LinkedApplicationGroupState() : group.getState();
    var members = LinkedGroupMembers.load(stateManager, memberIdsOrEmpty(state), context);
    eventAppender.append(LinkedGroupAssignmentDecider.decideGroupUnassign(state, members, command));
  }

  private static List<UUID> memberIdsOrEmpty(LinkedApplicationGroupState state) {
    return state.getMemberApplicationIds() == null ? List.of() : state.getMemberApplicationIds();
  }
}
