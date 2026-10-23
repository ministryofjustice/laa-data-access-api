package uk.gov.justice.laa.dstew.access.testutils;

import java.util.random.RandomGenerator;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;

/**
 * Valid lifecycle actions for a generated application. Auto-granted applications have no further
 * actions.
 */
public record ApplicationLifecycle(
    boolean autoGranted,
    boolean makeDecision,
    boolean assignCaseworker,
    DecisionStatus decisionStatus) {

  public ApplicationLifecycle(boolean autoGranted, boolean makeDecision, boolean assignCaseworker) {
    this(autoGranted, makeDecision, assignCaseworker, DecisionStatus.REFUSED);
  }

  public static ApplicationLifecycle select(RandomGenerator random) {
    if (random.nextDouble() < 0.175) {
      return new ApplicationLifecycle(true, false, false);
    }
    boolean decision = random.nextDouble() < 0.40;
    boolean assignment = random.nextDouble() < 0.70;
    return new ApplicationLifecycle(
        false,
        decision,
        assignment,
        decision ? random.nextBoolean() ? DecisionStatus.GRANTED : DecisionStatus.REFUSED : null);
  }

  public ApplicationLifecycle {
    if (autoGranted && (makeDecision || assignCaseworker)) {
      throw new IllegalArgumentException(
          "Auto-granted applications cannot have further lifecycle actions");
    }
    if (makeDecision && decisionStatus == null) {
      throw new IllegalArgumentException("A decision lifecycle requires a decision status");
    }
  }
}
