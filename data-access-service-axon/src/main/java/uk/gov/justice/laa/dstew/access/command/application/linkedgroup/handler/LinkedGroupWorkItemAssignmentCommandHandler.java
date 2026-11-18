package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMembers;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentChange;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignedWorkItems;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

/** Handles group assignment and unassignment by appending all member events together. */
@Component
public class LinkedGroupWorkItemAssignmentCommandHandler {

  /**
   * Assigns every active member of a linked group in one append and returns the members whose
   * assignment changed, addressed item first.
   */
  @CommandHandler
  public AssignedWorkItems handle(
      AssignLinkedGroupWorkItemCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    var state = LinkedApplicationGroupAggregate.stateOf(group);
    LinkedApplicationGroupDecider.validateMember(state, command.workItemId());
    var members = LinkedGroupMembers.load(stateManager, state.memberIdsOrEmpty(), context);
    LinkedGroupAssignmentDecider.validateGroupAssign(members, command);
    var changes = LinkedGroupAssignmentDecider.decideGroupAssign(members, command);
    eventAppender.append(changes);

    List<ApplicationDataId> dataIds =
        changes.stream()
            .map(WorkItemAssignmentChange::workItemId)
            .map(id -> new ApplicationDataId(id, members.get(id).getApplicationDataVersion()))
            .toList();
    Map<ApplicationDataId, ApplicationDataPayload> payloads = applicationDataStore.getAll(dataIds);
    return new AssignedWorkItems(
        dataIds.stream()
            .map(
                dataId ->
                    new AssignedWorkItems.AssignedItem(
                        dataId.applicationId(), payloads.get(dataId).laaReference()))
            .toList());
  }

  /** Unassigns every assigned active member of a linked group in one append. */
  @CommandHandler
  public void handle(
      UnassignLinkedGroupWorkItemCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var state = LinkedApplicationGroupAggregate.stateOf(group);
    LinkedApplicationGroupDecider.validateMember(state, command.workItemId());
    var members = LinkedGroupMembers.load(stateManager, state.memberIdsOrEmpty(), context);
    LinkedGroupAssignmentDecider.validateGroupUnassign(members, command);
    eventAppender.append(LinkedGroupAssignmentDecider.decideGroupUnassign(members, command));
  }
}
