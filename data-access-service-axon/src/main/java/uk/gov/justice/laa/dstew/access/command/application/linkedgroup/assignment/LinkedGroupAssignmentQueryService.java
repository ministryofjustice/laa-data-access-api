package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadRepository;

/**
 * Queries assignment state for applications and linked groups to determine which assignment rule
 * applies when an application is added to a linked group.
 */
@Service
@RequiredArgsConstructor
public class LinkedGroupAssignmentQueryService {
  private final ApplicationGroupRouteRepository groupRouteRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final WorkListItemReadRepository workListItemRepository;

  /**
   * Determines which of the 4 assignment rules applies when adding an application to a linked
   * group.
   *
   * @param applicationId the application being added to the group
   * @param groupId the linked group the application is being added to
   * @return the assignment rule that should be applied
   */
  public LinkedGroupAssignmentRule determineRule(UUID applicationId, UUID groupId) {
    var appAssignment = getWorkItemAssignment(applicationId);
    var groupAssignment = getLinkedGroupAssignment(groupId);

    // Scenario 1: App unassigned + Group assigned → assign app to group
    if (appAssignment.isEmpty() && groupAssignment.isPresent()) {
      return LinkedGroupAssignmentRule.ASSIGN_APP_TO_GROUP;
    }

    // Scenario 2: App assigned to different CW + Group assigned → reassign app
    if (appAssignment.isPresent()
        && groupAssignment.isPresent()
        && !appAssignment.get().isSameCaseworkerAs(groupAssignment.get())) {
      return LinkedGroupAssignmentRule.REASSIGN_APP_TO_GROUP;
    }

    // Scenario 3: App assigned + Group unassigned → assign group from app
    if (appAssignment.isPresent() && groupAssignment.isEmpty()) {
      return LinkedGroupAssignmentRule.ASSIGN_GROUP_FROM_APP;
    }

    // Scenario 4: Both unassigned or both same caseworker → no change
    return LinkedGroupAssignmentRule.NO_CHANGE;
  }

  /**
   * Retrieves the assignment state of a specific work item.
   *
   * @param workItemId the work item to query
   * @return the assignment state, or empty if not found or unassigned
   */
  public Optional<WorkItemAssignment> getWorkItemAssignment(UUID workItemId) {
    return workListItemRepository
        .findById(workItemId)
        .filter(item -> item.getAssigneeId() != null)
        .map(item -> new WorkItemAssignment(item.getAssigneeId(), item.getAssignmentVersion()));
  }

  /**
   * Retrieves the assignment state of a linked group by finding its lead application and querying
   * its assignment.
   *
   * @param groupId the linked group to query
   * @return the assignment state of the group, or empty if the group is unassigned
   * @throws ResourceNotFoundException if the group has no lead application
   */
  public Optional<WorkItemAssignment> getLinkedGroupAssignment(UUID groupId) {
    var leadApplicationId = findGroupLeadApplicationId(groupId);
    return getWorkItemAssignment(leadApplicationId);
  }

  /**
   * Finds the lead application ID for a linked group by querying the linked group read model.
   *
   * @param groupId the linked group
   * @return the application ID of the group lead
   * @throws ResourceNotFoundException if the group is not found
   */
  public UUID findGroupLeadApplicationId(UUID groupId) {
    var group =
        groupReadRepository
            .findById(groupId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No linked application group found with ID " + groupId));
    return group.getLeadApplicationId();
  }
}
