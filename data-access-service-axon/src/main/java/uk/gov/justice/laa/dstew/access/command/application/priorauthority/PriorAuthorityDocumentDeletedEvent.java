package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Persisted event emitted when a prior-authority draft document is marked deleted. */
@Event(namespace = "access", name = "PriorAuthorityDocumentDeleted")
public record PriorAuthorityDocumentDeletedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID documentId,
    @RetainedField Instant deletedAt,
    @RetainedField UUID parentApplicationId)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
  }

  @Override
  public Instant occurredAt() {
    return deletedAt;
  }
}
