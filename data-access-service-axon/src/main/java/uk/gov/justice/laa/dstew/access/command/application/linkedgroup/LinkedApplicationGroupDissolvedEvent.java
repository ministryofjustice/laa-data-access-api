package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Emitted when removing a member leaves a linked group with no active membership. */
@Event
public record LinkedApplicationGroupDissolvedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID formerLeadApplicationId,
    UUID removedApplicationId,
    List<UUID> memberApplicationIds,
    long groupVersion,
    Instant occurredAt,
    UUID justificationId)
    implements LinkedGroupMemberRemoval {

  /** Preserves callers and historical events without a justification reference. */
  public LinkedApplicationGroupDissolvedEvent(
      UUID groupId,
      UUID formerLeadApplicationId,
      UUID removedApplicationId,
      List<UUID> memberApplicationIds,
      long groupVersion,
      Instant occurredAt) {
    this(
        groupId,
        formerLeadApplicationId,
        removedApplicationId,
        memberApplicationIds,
        groupVersion,
        occurredAt,
        null);
  }

  @Override
  public LinkedApplicationGroupDissolvedEvent withJustification(UUID id) {
    return new LinkedApplicationGroupDissolvedEvent(
        groupId,
        formerLeadApplicationId,
        removedApplicationId,
        memberApplicationIds,
        groupVersion,
        occurredAt,
        id);
  }
}
