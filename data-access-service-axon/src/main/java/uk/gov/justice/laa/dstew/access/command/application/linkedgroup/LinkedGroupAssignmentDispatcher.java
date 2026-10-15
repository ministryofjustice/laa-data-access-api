package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/**
 * Applies assignment rules for linked group membership changes and dispatches assignment commands.
 *
 * <p>Implements the following scenarios when a member is added to a linked group:
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
public class LinkedGroupAssignmentDispatcher {

  private final ApplicationReadRepository applicationReadRepository;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final CommandGateway commandGateway;

  /**
   * Internal record encapsulating an assignment decision.
   *
   * <p>Contains the action to take, target application ID, and caseworker ID.
   */
  private record AssignmentDecision(
      LinkedGroupAssignmentDecisionAction action,
      UUID targetApplicationId,
      @Nullable UUID caseworkerId) {}

  /**
   * Applies assignment rules for a member added to a group and dispatches assignment commands if
   * needed.
   *
   * @param groupId the linked group ID
   * @param memberApplicationId the application being added to the group
   * @param occurredAt timestamp of when the membership change occurred
   */
  public void applyAssignmentRules(
      UUID groupId, UUID memberApplicationId, Instant occurredAt) {
    AssignmentDecision decision =
        determineAssignmentDecision(groupId, memberApplicationId);

    if (decision.action() == LinkedGroupAssignmentDecisionAction.ASSIGN_MEMBER_TO_LEAD
        || decision.action() == LinkedGroupAssignmentDecisionAction.ASSIGN_LEAD_TO_MEMBER) {
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

  /**
   * Determines the assignment decision when adding an application to a linked group.
   *
   * <p>Queries the current state of the group and applications, then applies the assignment rules.
   *
   * @param groupId the linked group ID
   * @param newMemberApplicationId the application being added to the group
   * @return the assignment decision with the action to take
   */
  private AssignmentDecision determineAssignmentDecision(
      UUID groupId, UUID newMemberApplicationId) {
    Optional<LinkedApplicationGroupReadModel> group = groupReadRepository.findById(groupId);
    if (group.isEmpty()) {
      return noAction();
    }

    UUID leadApplicationId = group.get().getLeadApplicationId();
    UUID leadCaseworkerId = getApplicationCaseworker(leadApplicationId);
    UUID memberCaseworkerId = getApplicationCaseworker(newMemberApplicationId);

    // AC1: Lead is assigned, member is not assigned so Assign member to lead's caseworker
    if (leadCaseworkerId != null && memberCaseworkerId == null) {
      return assignMemberToLead(newMemberApplicationId, leadCaseworkerId);
    }

    // AC2: Both are assigned but to different caseworkers so Reassign member to lead's caseworker
    if (leadCaseworkerId != null && !leadCaseworkerId.equals(memberCaseworkerId)) {
      return assignMemberToLead(newMemberApplicationId, leadCaseworkerId);
    }

    // AC3: Member is assigned, lead is not assigned so Assign lead to member's caseworker
    if (memberCaseworkerId != null && leadCaseworkerId == null) {
      return assignLeadToMember(leadApplicationId, memberCaseworkerId);
    }

    // AC4: Neither assigned or both assigned to same caseworker so No change needed
    return noAction();
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

  /** Creates an assignment decision to assign member to lead's caseworker. */
  private AssignmentDecision assignMemberToLead(UUID memberApplicationId, UUID caseworkerId) {
    return new AssignmentDecision(
        LinkedGroupAssignmentDecisionAction.ASSIGN_MEMBER_TO_LEAD,
        memberApplicationId,
        caseworkerId);
  }

  /** Creates an assignment decision to assign lead to member's caseworker. */
  private AssignmentDecision assignLeadToMember(UUID leadApplicationId, UUID caseworkerId) {
    return new AssignmentDecision(
        LinkedGroupAssignmentDecisionAction.ASSIGN_LEAD_TO_MEMBER, leadApplicationId, caseworkerId);
  }

  /** Creates an assignment decision with no action needed. */
  private AssignmentDecision noAction() {
    return new AssignmentDecision(LinkedGroupAssignmentDecisionAction.NONE, null, null);
  }
}
