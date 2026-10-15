package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * Applies assignment rules when applications are added to a linked group.
 *
 * <p>Reacts to group events and synchronizes assignments based on the current state of the group
 * and applications.
 */
@Component
@Namespace("linkedGroupAssignment")
@RequiredArgsConstructor
public class LinkedGroupAssignmentEventHandler {

  private final LinkedGroupAssignmentDispatcher linkedGroupAssignmentDispatcher;

  /**
   * Applies assignment rules when a new linked group is created with initial members.
   *
   * <p>Processes all non-lead members (indices 1+) to ensure consistent assignment behavior
   * regardless of whether members are added during group creation or individually.
   *
   * @param event the group creation event
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event) {
    var groupMemberApplicationIds = event.memberApplicationIds();
    if (groupMemberApplicationIds.size() >= 2) {
      groupMemberApplicationIds.stream().skip(1).forEach(memberId ->
          linkedGroupAssignmentDispatcher.applyAssignmentRules(
              event.groupId(), memberId, event.occurredAt())
      );
    }
  }

  /**
   * Applies assignment rules when an application is added to an existing group.
   *
   * @param event the member added event
   */
  @EventHandler
  public void on(MemberAddedToGroupEvent event) {
    linkedGroupAssignmentDispatcher.applyAssignmentRules(
        event.groupId(), event.memberId(), event.occurredAt());
  }
}
