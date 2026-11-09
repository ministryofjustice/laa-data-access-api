package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Emitted when the lead of a linked application group changes. */
@Event
public record LinkedApplicationGroupLeadChangedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID previousLeadApplicationId,
    UUID newLeadApplicationId,
    long groupVersion,
    Instant occurredAt,
    List<UUID> memberApplicationIds,
    UUID justificationId) {

  /** Preserves historical events whose full membership was not recorded. */
  public LinkedApplicationGroupLeadChangedEvent(
      UUID groupId,
      UUID previousLeadApplicationId,
      UUID newLeadApplicationId,
      long groupVersion,
      Instant occurredAt) {
    this(
        groupId,
        previousLeadApplicationId,
        newLeadApplicationId,
        groupVersion,
        occurredAt,
        null,
        null);
  }

  /** Attaches the accepted action's external payload reference. */
  public LinkedApplicationGroupLeadChangedEvent withJustification(UUID justificationRefId) {
    return new LinkedApplicationGroupLeadChangedEvent(
        groupId,
        previousLeadApplicationId,
        newLeadApplicationId,
        groupVersion,
        occurredAt,
        memberApplicationIds,
        justificationRefId);
  }
}
