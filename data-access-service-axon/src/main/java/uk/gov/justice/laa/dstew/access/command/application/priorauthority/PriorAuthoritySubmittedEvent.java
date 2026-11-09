package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/**
 * Thin-pointer event emitted when a Prior Authority draft is submitted. Contains no personal data —
 * PII remains in the prior_authority_data table at the referenced dataVersion.
 */
@Event(namespace = "access", name = "PriorAuthoritySubmitted")
public record PriorAuthoritySubmittedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    int schemaVersion,
    long dataVersion,
    Instant occurredAt)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
