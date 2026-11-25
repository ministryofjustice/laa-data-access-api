package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/**
 * Persisted event emitted on every prior-authority draft-body update. Carries the full replacement
 * draft content as erasable event content, so the aggregate folds the current draft from its own
 * stream. Routing every draft write through the aggregate also serialises concurrent writers.
 */
@Event(namespace = "access", name = "PriorAuthorityDraftUpdated")
public record PriorAuthorityDraftUpdatedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID parentApplicationId,
    @RetainedField Instant occurredAt,
    @Nullable PriorAuthorityContent content,
    @Nullable String serialisedRequest)
    implements GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
