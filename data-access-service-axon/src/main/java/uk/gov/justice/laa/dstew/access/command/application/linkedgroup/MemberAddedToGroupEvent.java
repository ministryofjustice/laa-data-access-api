package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Emitted when an application is added to an already-existing {@link
 * LinkedApplicationGroupAggregate}.
 */
@Event
public record MemberAddedToGroupEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID leadApplicationId,
    UUID memberId,
    Instant occurredAt,
    List<UUID> memberApplicationIds,
    UUID justificationId) {

  /** Preserves historical events whose full membership was not recorded. */
  public MemberAddedToGroupEvent(
      UUID groupId, UUID leadApplicationId, UUID memberId, Instant occurredAt) {
    this(groupId, leadApplicationId, memberId, occurredAt, null, null);
  }

  /** Attaches the accepted action's external payload reference. */
  public MemberAddedToGroupEvent withJustification(UUID justificationRefId) {
    return new MemberAddedToGroupEvent(
        groupId, leadApplicationId, memberId, occurredAt, memberApplicationIds, justificationRefId);
  }
}
