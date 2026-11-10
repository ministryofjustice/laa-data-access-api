package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Persisted event emitted when a Prior Authority document type is set or replaced. */
@Event
public record PriorAuthorityDocumentTypeUpdatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    String documentType,
    Instant occurredAt,
    UUID parentApplicationId)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
  }
}
