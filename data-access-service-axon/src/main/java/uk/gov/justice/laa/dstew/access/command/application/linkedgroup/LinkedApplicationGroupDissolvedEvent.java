package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Emitted when removing a member leaves a linked group with no active membership. */
@Event(namespace = "access", name = "LinkedApplicationGroupDissolved")
public record LinkedApplicationGroupDissolvedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID formerLeadApplicationId,
    UUID removedApplicationId,
    List<UUID> memberApplicationIds,
    long groupVersion,
    Instant occurredAt)
    implements LinkedGroupMemberRemoval, GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(memberApplicationIds, formerLeadApplicationId, removedApplicationId);
  }
}
