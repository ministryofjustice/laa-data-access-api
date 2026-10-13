package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecision.Action;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;

/**
 * Dispatches assignment commands for linked group membership changes.
 *
 * <p>Applies linked group assignment rules and dispatches assignment commands when needed.
 */
@Service
@RequiredArgsConstructor
public class LinkedGroupAssignmentDispatcher {

  private final LinkedGroupAssignmentRulesService linkedGroupAssignmentRulesService;
  private final CommandGateway commandGateway;

  /**
   * Applies assignment rules for a member added to a group and dispatches assignment commands if
   * needed.
   *
   * @param groupId the linked group ID
   * @param memberApplicationId the application being added to the group
   * @param occurredAt timestamp of when the membership change occurred
   */
  public void applyAssignmentRules(UUID groupId, UUID memberApplicationId, Instant occurredAt) {
    LinkedGroupAssignmentDecision decision =
        linkedGroupAssignmentRulesService.applyRules(groupId, memberApplicationId);

    if (decision.action() == Action.ASSIGN_MEMBER_TO_LEAD
        || decision.action() == Action.ASSIGN_LEAD_TO_MEMBER) {
      commandGateway.sendAndWait(
          new DirectGroupWorkItemAssignmentCommand(
              decision.targetApplicationId(),
              decision.caseworkerId(),
              null,
              "",
              "Assignments for application and linked groups synchronized",
              occurredAt));
    }
  }
}
