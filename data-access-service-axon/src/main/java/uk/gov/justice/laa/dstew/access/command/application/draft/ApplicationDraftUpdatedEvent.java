package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/**
 * Thin, PII-free event emitted on every Application draft-body mutation.
 *
 * <p>Carries no draft content. It routes each draft write through the aggregate's per-stream
 * optimistic-concurrency gate so concurrent writers serialise instead of silently overwriting.
 */
@Event
public record ApplicationDraftUpdatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId, Instant occurredAt)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
