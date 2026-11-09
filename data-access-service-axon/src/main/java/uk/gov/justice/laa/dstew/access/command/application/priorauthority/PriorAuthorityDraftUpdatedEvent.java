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
 * draft content, so the aggregate folds the current draft from its own stream.
 */
@Event(namespace = "access", name = "PriorAuthorityDraftUpdated")
public record PriorAuthorityDraftUpdatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID parentApplicationId,
    Instant occurredAt,
    Erasable<PriorAuthorityContent> content,
    Erasable<String> serialisedRequest)
    implements GenesisScoped {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public PriorAuthorityDraftUpdatedEvent {
    content = Erasable.orErased(content);
    serialisedRequest = Erasable.orErased(serialisedRequest);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
  }
}
