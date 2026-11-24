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

/**
 * Emitted when an application is added to an already-existing {@link
 * LinkedApplicationGroupAggregate}.
 */
@Event(namespace = "access", name = "MemberAddedToGroup")
public record MemberAddedToGroupEvent(
    @RetainedField @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    @RetainedField UUID leadApplicationId,
    @RetainedField UUID memberId,
    @RetainedField Instant occurredAt,
    @RetainedField List<UUID> memberApplicationIds,
    @RetainedField UUID justificationId)
    implements GenesisScoped {

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

  /**
   * Names the lead, the member and the group's members at the time, when recorded. Events stored
   * before {@code leadApplicationId} was recorded name only the member.
   */
  @Override
  public Set<UUID> genesisIds() {
    return leadApplicationId == null
        ? GenesisScoped.ids(memberId)
        : GenesisScoped.ids(
            Objects.requireNonNullElse(memberApplicationIds, List.of()),
            leadApplicationId,
            memberId);
  }
}
