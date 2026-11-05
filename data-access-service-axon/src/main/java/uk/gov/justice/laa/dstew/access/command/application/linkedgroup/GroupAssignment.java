package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;

/** The shared assignment of a set of linked applications, worked out from their active members. */
public sealed interface GroupAssignment {
  /** Every active member is assigned to {@code caseworkerId}. */
  record Assigned(UUID caseworkerId) implements GroupAssignment {}

  /** Every active member is unassigned. */
  record Unassigned() implements GroupAssignment {}

  /** There are no active members. */
  record None() implements GroupAssignment {}
}
