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
   * @param event the group creation event
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event) {
    if (event.memberApplicationIds().size() >= 2) {
      linkedGroupAssignmentDispatcher.applyAssignmentRules(
          event.groupId(), event.memberApplicationIds().get(1), event.occurredAt());
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
