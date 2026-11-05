package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builds linked-group states for pure decider tests. */
final class LinkedApplicationGroupStates {
  private LinkedApplicationGroupStates() {}

  static LinkedApplicationGroupState group(UUID groupId, List<UUID> memberIds) {
    var state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(
        state,
        new LinkedApplicationGroupCreatedEvent(
            groupId, memberIds.getFirst(), memberIds, Instant.EPOCH));
    return state;
  }

  static LinkedApplicationGroupState dissolved(UUID groupId, List<UUID> memberIds) {
    var state = group(groupId, memberIds);
    LinkedApplicationGroupEvolve.apply(
        state,
        new LinkedApplicationGroupDissolvedEvent(
            groupId, memberIds.getFirst(), memberIds.getFirst(), List.of(), 1L, Instant.EPOCH));
    return state;
  }
}
