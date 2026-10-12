package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/**
 * Coordinates the application of assignment rules when an application is added to a linked group.
 * Determines which of 4 scenarios applies and dispatches the appropriate assignment commands.
 */
@Service
@RequiredArgsConstructor
public class LinkedGroupAssignmentCoordinator {
  private final LinkedGroupAssignmentQueryService queryService;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final CommandGateway commandGateway;

  /**
   * Applies assignment rules when an application is added to a linked group.
   *
   * <p>This method examines the current assignment state of both the application and the group,
   * determines which of 4 scenarios applies, and dispatches assignment commands as needed.
   *
   * @param applicationId the application being added to the group
   * @param groupId the linked group the application is being added to
   * @param occurredAt the timestamp of the linking operation
   */
  public void applyAssignmentRulesWhenLinking(
      UUID applicationId, UUID groupId, Instant occurredAt) {
    var rule = queryService.determineRule(applicationId, groupId);

    switch (rule) {
      case ASSIGN_APP_TO_GROUP:
        assignAppToGroup(applicationId, groupId, occurredAt);
        break;

      case REASSIGN_APP_TO_GROUP:
        reassignAppToGroup(applicationId, groupId, occurredAt);
        break;

      case ASSIGN_GROUP_FROM_APP:
        assignGroupFromApp(applicationId, groupId, occurredAt);
        break;

      case NO_CHANGE:
        // Do nothing - both unassigned or same caseworker
        break;

      default:
        throw new IllegalStateException("Unexpected assignment rule: " + rule);
    }
  }

  /**
   * Scenario 1: Application is unassigned and group is assigned. Assigns the application to the
   * group's caseworker.
   */
  private void assignAppToGroup(UUID applicationId, UUID groupId, Instant occurredAt) {
    var groupAssignment =
        queryService
            .getLinkedGroupAssignment(groupId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Group " + groupId + " should be assigned for Scenario 1"));

    commandGateway.sendAndWait(
        new AssignWorkItemCommand(
            applicationId,
            groupAssignment.caseworkerId(),
            0L,
            null,
            "Application added to assigned group",
            occurredAt));
  }

  /**
   * Scenario 2: Application is assigned to a different caseworker than the group. Reassigns the
   * application to the group's caseworker.
   */
  private void reassignAppToGroup(UUID applicationId, UUID groupId, Instant occurredAt) {
    var groupAssignment =
        queryService
            .getLinkedGroupAssignment(groupId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Group " + groupId + " should be assigned for Scenario 2"));

    commandGateway.sendAndWait(
        new AssignWorkItemCommand(
            applicationId,
            groupAssignment.caseworkerId(),
            0L,
            null,
            "Application reassigned to group caseworker",
            occurredAt));
  }

  /**
   * Scenario 3: Application is assigned and group is unassigned. Assigns the group from the
   * application's caseworker.
   *
   * <p>This elegantly reuses the existing group assignment propagation by dispatching an
   * AssignWorkItemCommand for the group lead. The AssignWorkItemCommandHandler detects the group
   * membership and automatically propagates the assignment to all members.
   */
  private void assignGroupFromApp(UUID applicationId, UUID groupId, Instant occurredAt) {
    var appAssignment =
        queryService
            .getWorkItemAssignment(applicationId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Application " + applicationId + " should be assigned for Scenario 3"));

    var groupLeadId = findGroupLeadApplicationId(groupId);

    commandGateway.sendAndWait(
        new AssignWorkItemCommand(
            groupLeadId,
            appAssignment.caseworkerId(),
            0L,
            null,
            "Group assigned from application",
            occurredAt));
  }

  private UUID findGroupLeadApplicationId(UUID groupId) {
    return groupReadRepository
        .findById(groupId)
        .map(LinkedApplicationGroupReadModel::getLeadApplicationId)
        .orElseThrow(
            () ->
                new IllegalStateException("No linked application group found with ID " + groupId));
  }
}
