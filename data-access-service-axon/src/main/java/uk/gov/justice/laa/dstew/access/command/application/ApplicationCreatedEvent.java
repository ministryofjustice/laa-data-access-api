package uk.gov.justice.laa.dstew.access.command.application;

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
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Establishes an Application aggregate. The submitted content is erasable content; proceeding IDs
 * are kept as linking keys.
 */
@Event(namespace = "access", name = "ApplicationCreated")
public record ApplicationCreatedEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField long applicationDataVersion,
    @RetainedField(nullable = true) String requestFingerprint,
    @RetainedField(nullable = true) String status,
    @RetainedField int schemaVersion,
    @RetainedField Instant occurredAt,
    @Nullable List<PotentialDuplicate> potentialDuplicates,
    @RetainedField(nullable = true) String officeCode,
    @RetainedField String laaReference,
    @RetainedField List<UUID> proceedingIds,
    @Nullable ParsedAppContentDetails content)
    implements GenesisScoped {

  /** Normalises missing proceeding IDs to none. */
  public ApplicationCreatedEvent {
    proceedingIds = List.copyOf(Objects.requireNonNullElse(proceedingIds, List.of()));
  }

  /** Creates the event without submitted content or proceedings. */
  public ApplicationCreatedEvent(
      UUID applicationId,
      long applicationDataVersion,
      String requestFingerprint,
      String status,
      int schemaVersion,
      Instant occurredAt,
      @Nullable List<PotentialDuplicate> potentialDuplicates,
      String officeCode,
      String laaReference) {
    this(
        applicationId,
        applicationDataVersion,
        requestFingerprint,
        status,
        schemaVersion,
        occurredAt,
        potentialDuplicates,
        officeCode,
        laaReference,
        List.of(),
        null);
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
