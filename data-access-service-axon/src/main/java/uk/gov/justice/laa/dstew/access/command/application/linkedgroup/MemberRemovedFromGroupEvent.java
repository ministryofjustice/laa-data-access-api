package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Emitted when a non-lead application leaves a linked group that remains active. */
@Event(namespace = "access", name = "MemberRemovedFromGroup")
public record MemberRemovedFromGroupEvent(
    @RetainedField @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    @RetainedField UUID leadApplicationId,
    @RetainedField UUID memberId,
    @RetainedField long groupVersion,
    @RetainedField Instant occurredAt,
    @RetainedField(nullable = true) List<UUID> memberApplicationIds,
    @RetainedField(nullable = true) UUID justificationId)
    implements LinkedGroupMemberRemoval, GenesisScoped {

  /** Preserves historical events whose full membership was not recorded. */
  public MemberRemovedFromGroupEvent(
      UUID groupId, UUID leadApplicationId, UUID memberId, long groupVersion, Instant occurredAt) {
    this(groupId, leadApplicationId, memberId, groupVersion, occurredAt, null, null);
  }

  @Override
  public MemberRemovedFromGroupEvent withJustification(UUID justificationRefId) {
    return new MemberRemovedFromGroupEvent(
        groupId,
        leadApplicationId,
        memberId,
        groupVersion,
        occurredAt,
        memberApplicationIds,
        justificationRefId);
  }

  /** Names the group's members at the time, when recorded; older events name fewer. */
  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(
        Objects.requireNonNullElse(memberApplicationIds, List.of()), leadApplicationId, memberId);
  }
}
