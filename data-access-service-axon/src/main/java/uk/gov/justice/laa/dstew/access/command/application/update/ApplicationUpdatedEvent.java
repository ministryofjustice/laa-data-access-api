package uk.gov.justice.laa.dstew.access.command.application.update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/**
 * Replaces an Application's content. The replacement content and request are erasable content;
 * proceeding IDs are kept as linking keys.
 */
@Event(namespace = "access", name = "ApplicationUpdated")
public record ApplicationUpdatedEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField long applicationVersion,
    @RetainedField long applicationDataVersion,
    @RetainedField String previousStatus,
    @RetainedField String status,
    @RetainedField Instant occurredAt,
    @RetainedField List<UUID> proceedingIds,
    @Nullable ParsedAppContentDetails content,
    @Nullable String serialisedRequest)
    implements GenesisScoped {

  /** Normalises missing proceeding IDs to none. */
  public ApplicationUpdatedEvent {
    proceedingIds = List.copyOf(Objects.requireNonNullElse(proceedingIds, List.of()));
  }

  /** Creates the event without replacement content or proceedings. */
  public ApplicationUpdatedEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String previousStatus,
      String status,
      Instant occurredAt) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        previousStatus,
        status,
        occurredAt,
        List.of(),
        null,
        null);
  }

  public boolean enteredSubmitted() {
    return !"APPLICATION_SUBMITTED".equals(previousStatus)
        && "APPLICATION_SUBMITTED".equals(status);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
