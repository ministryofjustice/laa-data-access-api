package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/**
 * Event emitted when a Prior Authority draft is submitted. It carries no content: the aggregate
 * seals the draft content folded from the draft events. The expert type is kept plain so that the
 * work list can filter on it.
 */
@Event(namespace = "access", name = "PriorAuthoritySubmitted")
public record PriorAuthoritySubmittedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    int schemaVersion,
    String expertType,
    Instant occurredAt)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
