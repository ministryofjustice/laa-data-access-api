package uk.gov.justice.laa.dstew.access.command.application;

/** Explicit automatic-assessment state. */
public enum AutoGrantedState {
  PENDING,
  AUTOGRANTED,
  MANUAL;

  /** Converts the auto-grant flag carried by the general-decision command. */
  public static AutoGrantedState fromDecisionFlag(Boolean value) {
    return value == null ? PENDING : value ? AUTOGRANTED : MANUAL;
  }
}
