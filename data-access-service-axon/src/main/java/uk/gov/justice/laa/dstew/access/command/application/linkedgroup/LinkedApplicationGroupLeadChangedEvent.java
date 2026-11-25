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

/** Emitted when the lead of a linked application group changes. */
@Event(namespace = "access", name = "LinkedApplicationGroupLeadChanged")
public record LinkedApplicationGroupLeadChangedEvent(
    @RetainedField @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    @RetainedField UUID previousLeadApplicationId,
    @RetainedField UUID newLeadApplicationId,
    @RetainedField long groupVersion,
    @RetainedField Instant occurredAt,
    @RetainedField(nullable = true) List<UUID> memberApplicationIds,
    @RetainedField(nullable = true) UUID justificationId)
    implements GenesisScoped {

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

  /** Names the group's members at the time, when recorded; older events name fewer. */
  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(
        Objects.requireNonNullElse(memberApplicationIds, List.of()),
        previousLeadApplicationId,
        newLeadApplicationId);
  }
}
