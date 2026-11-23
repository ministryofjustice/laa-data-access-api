package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/**
 * Persisted event emitted on every prior-authority draft-body update. Carries the full replacement
 * draft content as erasable event content, so the aggregate folds the current draft from its own
 * stream. Routing every draft write through the aggregate also serialises concurrent writers.
 */
@Event(namespace = "access", name = "PriorAuthorityDraftUpdated")
public record PriorAuthorityDraftUpdatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID parentApplicationId,
    Instant occurredAt,
    Erasable<PriorAuthorityContent> content,
    Erasable<String> serialisedRequest)
    implements GenesisScoped {

  /** Treats absent content as erased, as in an erased content row or a kept-fields-only read. */
  public PriorAuthorityDraftUpdatedEvent {
    content = Erasable.orErased(content);
    serialisedRequest = Erasable.orErased(serialisedRequest);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
  }
}
