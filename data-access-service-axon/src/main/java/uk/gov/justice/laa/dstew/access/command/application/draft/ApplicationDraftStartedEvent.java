package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Event indicating that an Application draft has been started for the given ID. It carries the
 * draft's content, which is stored apart from the event so that it can be erased.
 */
@Event(namespace = "access", name = "ApplicationDraftStarted")
public record ApplicationDraftStartedEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField int schemaVersion,
    @RetainedField(nullable = true) String requestFingerprint,
    @RetainedField Instant occurredAt,
    @RetainedField(nullable = true) String officeCode,
    @RetainedField(nullable = true) String status,
    @RetainedField(nullable = true) String laaReference,
    @Nullable Map<String, Object> applicationContent,
    @Nullable List<PotentialDuplicate> potentialDuplicates)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
