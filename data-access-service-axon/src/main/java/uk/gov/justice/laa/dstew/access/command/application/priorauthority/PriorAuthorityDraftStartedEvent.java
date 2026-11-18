package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Event produced when a Prior Authority draft is first created. */
@Event(namespace = "access", name = "PriorAuthorityDraftStarted")
public record PriorAuthorityDraftStartedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    int schemaVersion,
    Instant occurredAt,
    String officeCode)
    implements GenesisScoped {

  public PriorAuthorityDraftStartedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      Instant occurredAt) {
    this(priorAuthorityId, applicationId, priorAuthorityType, schemaVersion, occurredAt, null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
