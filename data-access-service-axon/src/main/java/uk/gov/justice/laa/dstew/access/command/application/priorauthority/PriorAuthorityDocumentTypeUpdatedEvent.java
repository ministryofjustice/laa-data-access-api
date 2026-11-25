package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Persisted event emitted when a Prior Authority document type is set or replaced. */
@Event(namespace = "access", name = "PriorAuthorityDocumentTypeUpdated")
public record PriorAuthorityDocumentTypeUpdatedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID documentId,
    @Nullable String documentType,
    @RetainedField Instant occurredAt,
    @RetainedField UUID parentApplicationId)
    implements GenesisScoped {

  /** Events stored before {@code parentApplicationId} was recorded name no genesis. */
  @Override
  public Set<UUID> genesisIds() {
    return parentApplicationId == null ? Set.of() : GenesisScoped.ids(parentApplicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
