package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Encapsulates the assignment decision for a linked group change. */
public record LinkedGroupAssignmentDecision(
    Action action, UUID targetApplicationId, @Nullable UUID caseworkerId) {

  /** Describes what assignment action, if any, should be taken. */
  public enum Action {
    ASSIGN_MEMBER_TO_LEAD,
    ASSIGN_LEAD_TO_MEMBER,
    NONE
  }

  public static LinkedGroupAssignmentDecision assignMemberToLead(
      UUID memberApplicationId, UUID caseworkerId) {
    return new LinkedGroupAssignmentDecision(
        Action.ASSIGN_MEMBER_TO_LEAD, memberApplicationId, caseworkerId);
  }

  public static LinkedGroupAssignmentDecision assignLeadToMember(
      UUID leadApplicationId, UUID caseworkerId) {
    return new LinkedGroupAssignmentDecision(
        Action.ASSIGN_LEAD_TO_MEMBER, leadApplicationId, caseworkerId);
  }

  public static LinkedGroupAssignmentDecision none() {
    return new LinkedGroupAssignmentDecision(Action.NONE, null, null);
  }
}
