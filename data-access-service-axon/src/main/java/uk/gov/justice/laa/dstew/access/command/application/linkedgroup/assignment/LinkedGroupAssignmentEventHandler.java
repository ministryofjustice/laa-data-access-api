package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;

/**
 * Applies assignment rules when an application is added to a linked group. Ensures that assignments
 * between applications and groups are consistent according to the four defined scenarios.
 *
 * <p>This event handler listens for {@link MemberAddedToGroupEvent} and coordinates the application
 * of assignment rules that determine how caseworker assignments should be adjusted when linking
 * applications together.
 */
@Component
@RequiredArgsConstructor
@Namespace("linked-group-assignment")
public class LinkedGroupAssignmentEventHandler {
  private final LinkedGroupAssignmentCoordinator coordinator;

  /**
   * When an application is added to a linked group, applies assignment rules to ensure the
   * application and group are assigned consistently.
   *
   * <p>The four scenarios handled are:
   *
   * <p>1. Application unassigned + Group assigned → Assign application to group's caseworker
   *
   * <p>2. Application assigned to different caseworker + Group assigned → Reassign application to
   * group's caseworker
   *
   * <p>3. Application assigned + Group unassigned → Assign group to application's caseworker
   *
   * <p>4. Both unassigned or both assigned to same caseworker → No changes
   *
   * @param event the event indicating an application was added to a linked group
   */
  @EventHandler
  public void on(MemberAddedToGroupEvent event) {
    coordinator.applyAssignmentRulesWhenLinking(
        event.memberId(), event.groupId(), event.occurredAt());
  }
}
