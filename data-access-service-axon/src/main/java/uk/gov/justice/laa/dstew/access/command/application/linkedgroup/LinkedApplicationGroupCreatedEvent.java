package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/**
 * Emitted by {@code LinkedApplicationGroupAggregate} once the group is fully initialised. Consumed
 * by projections to record group membership and derive {@code isLead} on each member's read model.
 */
@Event(namespace = "access", name = "LinkedApplicationGroupCreated")
public record LinkedApplicationGroupCreatedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID leadApplicationId,
    List<UUID> memberApplicationIds,
    Instant occurredAt,
    UUID justificationId)
    implements GenesisScoped {

  /** Preserves callers and historical events without a justification reference. */
  public LinkedApplicationGroupCreatedEvent(
      UUID groupId, UUID leadApplicationId, List<UUID> memberApplicationIds, Instant occurredAt) {
    this(groupId, leadApplicationId, memberApplicationIds, occurredAt, null);
  }

  /** Attaches the accepted action's external payload reference. */
  public LinkedApplicationGroupCreatedEvent withJustification(UUID justificationRefId) {
    return new LinkedApplicationGroupCreatedEvent(
        groupId, leadApplicationId, memberApplicationIds, occurredAt, justificationRefId);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(memberApplicationIds, leadApplicationId);
  }
}
