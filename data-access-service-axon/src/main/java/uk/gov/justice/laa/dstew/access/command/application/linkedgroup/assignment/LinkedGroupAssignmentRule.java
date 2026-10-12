package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

/**
 * Enumeration of assignment rules that determine how to handle caseworker assignments when an
 * application is added to a linked group.
 */
public enum LinkedGroupAssignmentRule {
  /** Application is unassigned, group is assigned: assign app to group's caseworker. */
  ASSIGN_APP_TO_GROUP,

  /** Application is assigned to different caseworker, group is assigned: reassign app. */
  REASSIGN_APP_TO_GROUP,

  /** Application is assigned, group is unassigned: assign group from app's caseworker. */
  ASSIGN_GROUP_FROM_APP,

  /** Both unassigned or both assigned to same caseworker: no change needed. */
  NO_CHANGE
}
