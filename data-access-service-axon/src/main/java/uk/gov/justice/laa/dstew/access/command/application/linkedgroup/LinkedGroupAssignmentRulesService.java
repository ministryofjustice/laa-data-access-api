package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

/**
 * Applies assignment rules when an application is added to a linked family group.
 *
 * <p>Implements the following scenarios:
 *
 * <ol>
 *   <li>If the lead application is assigned and the new member is not, assign the member to the
 *       lead's caseworker.
 *   <li>If both are assigned but to different caseworkers, reassign the member to the lead's
 *       caseworker.
 *   <li>If the new member is assigned and the lead is not, assign the lead to the member's
 *       caseworker.
 *   <li>If neither are assigned or both are assigned to the same caseworker, no change is needed.
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class LinkedGroupAssignmentRulesService {

  private final ApplicationReadRepository applicationReadRepository;

  /**
   * Determines the assignment decision when adding an application to a linked group.
   *
   * <p>Queries the current state of the applications and applies the assignment rules.
   *
   * @param leadApplicationId the lead application ID of the linked group
   * @param newMemberApplicationId the application being added to the group
   * @return the assignment decision, or {@link LinkedGroupAssignmentDecision#none()} if no action
   *     is needed
   */
  public LinkedGroupAssignmentDecision applyRules(
      UUID leadApplicationId, UUID newMemberApplicationId) {
    UUID leadCaseworkerId = getApplicationCaseworker(leadApplicationId);
    UUID memberCaseworkerId = getApplicationCaseworker(newMemberApplicationId);

    // AC1: Lead is assigned, member is not assigned so Assign member to lead's caseworker
    if (leadCaseworkerId != null && memberCaseworkerId == null) {
      return LinkedGroupAssignmentDecision.assignMemberToLead(
          newMemberApplicationId, leadCaseworkerId);
    }

    // AC2: Both are assigned but to different caseworkers so Reassign member to lead's caseworker
    if (leadCaseworkerId != null && !leadCaseworkerId.equals(memberCaseworkerId)) {
      return LinkedGroupAssignmentDecision.assignMemberToLead(
          newMemberApplicationId, leadCaseworkerId);
    }

    // AC3: Member is assigned, lead is not assigned so Assign lead to member's caseworker
    if (memberCaseworkerId != null && leadCaseworkerId == null) {
      return LinkedGroupAssignmentDecision.assignLeadToMember(
          leadApplicationId, memberCaseworkerId);
    }

    // AC4: Neither assigned or both assigned to same caseworker so No change needed
    return LinkedGroupAssignmentDecision.none();
  }

  /**
   * Gets the caseworker ID for an application, or null if unassigned.
   *
   * @param applicationId the application to query
   * @return the caseworker ID, or null if the application doesn't exist or is unassigned
   */
  private @Nullable UUID getApplicationCaseworker(UUID applicationId) {
    return applicationReadRepository
        .findById(applicationId)
        .map(ApplicationReadModel::getCaseworkerId)
        .orElse(null);
  }
}
