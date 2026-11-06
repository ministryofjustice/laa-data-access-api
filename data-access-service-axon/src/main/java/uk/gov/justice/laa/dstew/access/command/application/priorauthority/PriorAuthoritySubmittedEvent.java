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
 *
 * <p>{@code applicationDataVersion} pins the parent application's immutable data version as it
 * stood when the PA was submitted, so the work-list projection can hydrate parent-application
 * fields deterministically on replay. It is a nullable {@link Long} so that events persisted before
 * this field existed still deserialize under Jackson (which rejects {@code null} into a primitive);
 * absent is normalised to {@code 0L} — the parent application's original data version, which always
 * exists.
 */
@Event(namespace = "access", name = "PriorAuthoritySubmitted")
public record PriorAuthoritySubmittedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    int schemaVersion,
    long dataVersion,
    Long applicationDataVersion,
    Instant occurredAt)
    implements GenesisScoped {

  /** Normalises a missing (pre-field) {@code applicationDataVersion} to {@code 0L}. */
  public PriorAuthoritySubmittedEvent {
    if (applicationDataVersion == null) {
      applicationDataVersion = 0L;
    }
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
