package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Event indicating that an Application draft has been started for the given ID. */
@Event
public record ApplicationDraftStartedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    int schemaVersion,
    String requestFingerprint,
    Instant occurredAt,
    String officeCode)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
