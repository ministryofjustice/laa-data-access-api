package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.ArrayList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;

/** Event-fold functions for {@link LinkedApplicationGroupState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedApplicationGroupEvolve {

  /** Applies a {@link LinkedApplicationGroupCreatedEvent} to the given state. */
  public static void apply(
      LinkedApplicationGroupState state, LinkedApplicationGroupCreatedEvent event) {
    state.groupId = event.groupId();
    state.leadApplicationId = event.leadApplicationId();
    state.memberApplicationIds = new ArrayList<>(event.memberApplicationIds());
    state.groupVersion = 0L;
    state.caseworkerId = null;
    state.assignmentVersion = 0L;
  }

  /** Applies a {@link MemberAddedToGroupEvent} to the given state. */
  public static void apply(LinkedApplicationGroupState state, MemberAddedToGroupEvent event) {
    state.memberApplicationIds.add(event.memberId());
    state.groupVersion++;
  }

  /** Applies a {@link LinkedApplicationGroupLeadChangedEvent} to the given state. */
  public static void apply(
      LinkedApplicationGroupState state, LinkedApplicationGroupLeadChangedEvent event) {
    state.leadApplicationId = event.newLeadApplicationId();
    state.groupVersion = event.groupVersion();
  }

  /** Applies a {@link MemberRemovedFromGroupEvent} to the given state. */
  public static void apply(LinkedApplicationGroupState state, MemberRemovedFromGroupEvent event) {
    state.memberApplicationIds.remove(event.memberId());
    state.groupVersion = event.groupVersion();
  }

  /** Applies a {@link LinkedApplicationGroupDissolvedEvent} to the given state. */
  public static void apply(
      LinkedApplicationGroupState state, LinkedApplicationGroupDissolvedEvent event) {
    state.memberApplicationIds = new ArrayList<>();
    state.dissolved = true;
    state.groupVersion = event.groupVersion();
  }

  /** Applies a {@link LinkedGroupAssignedEvent} to the given state. */
  public static void apply(LinkedApplicationGroupState state, LinkedGroupAssignedEvent event) {
    state.caseworkerId = event.caseworkerId();
    state.assignmentVersion = event.assignmentVersion();
  }

  /** Applies a {@link LinkedGroupUnassignedEvent} to the given state. */
  public static void apply(LinkedApplicationGroupState state, LinkedGroupUnassignedEvent event) {
    state.caseworkerId = null;
    state.assignmentVersion = event.assignmentVersion();
  }
}
