package uk.gov.justice.laa.dstew.access.testutils;

import uk.gov.justice.laa.dstew.access.model.DecisionStatus;

/**
 * Valid lifecycle actions for a generated application. Auto-granted applications have no further
 * actions, and unassignment requires a prior assignment.
 */
public record ApplicationLifecycle(
    boolean autoGranted,
    boolean makeDecision,
    boolean assignCaseworker,
    boolean unassignCaseworker,
    DecisionStatus decisionStatus) {

  public ApplicationLifecycle(
      boolean autoGranted,
      boolean makeDecision,
      boolean assignCaseworker,
      boolean unassignCaseworker) {
    this(autoGranted, makeDecision, assignCaseworker, unassignCaseworker, DecisionStatus.REFUSED);
  }

  public ApplicationLifecycle {
    if (autoGranted && (makeDecision || assignCaseworker || unassignCaseworker)) {
      throw new IllegalArgumentException(
          "Auto-granted applications cannot have further lifecycle actions");
    }
    if (unassignCaseworker && !assignCaseworker) {
      throw new IllegalArgumentException("Cannot unassign without prior assignment");
    }
    if (makeDecision && decisionStatus == null) {
      throw new IllegalArgumentException("A decision lifecycle requires a decision status");
    }
  }
}
