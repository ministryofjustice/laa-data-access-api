package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Emitted when a non-lead application leaves a linked group that remains active. */
@Event(namespace = "access", name = "MemberRemovedFromGroup")
public record MemberRemovedFromGroupEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID leadApplicationId,
    UUID memberId,
    long groupVersion,
    Instant occurredAt)
    implements LinkedGroupMemberRemoval, GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(leadApplicationId, memberId);
  }
}
