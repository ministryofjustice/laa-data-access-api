package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Emitted when removing a member leaves a linked group with no active membership. */
@Event(namespace = "access", name = "LinkedApplicationGroupDissolved")
public record LinkedApplicationGroupDissolvedEvent(
    @RetainedField @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    @RetainedField UUID formerLeadApplicationId,
    @RetainedField UUID removedApplicationId,
    @RetainedField List<UUID> memberApplicationIds,
    @RetainedField long groupVersion,
    @RetainedField Instant occurredAt,
    @RetainedField UUID justificationId)
    implements LinkedGroupMemberRemoval, GenesisScoped {

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
  public LinkedApplicationGroupDissolvedEvent withJustification(UUID justificationRefId) {
    return new LinkedApplicationGroupDissolvedEvent(
        groupId,
        formerLeadApplicationId,
        removedApplicationId,
        memberApplicationIds,
        groupVersion,
        occurredAt,
        justificationRefId);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(memberApplicationIds, formerLeadApplicationId, removedApplicationId);
  }
}
