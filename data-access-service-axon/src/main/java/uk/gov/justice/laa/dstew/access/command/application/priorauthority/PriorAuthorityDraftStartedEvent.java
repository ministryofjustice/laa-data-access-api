package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Event produced when a Prior Authority draft is first created; carries the draft content. */
@Event(namespace = "access", name = "PriorAuthorityDraftStarted")
public record PriorAuthorityDraftStartedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    int schemaVersion,
    Instant occurredAt,
    String officeCode,
    Erasable<PriorAuthorityContent> content,
    Erasable<String> serialisedRequest)
    implements GenesisScoped {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public PriorAuthorityDraftStartedEvent {
    content = Erasable.orErased(content);
    serialisedRequest = Erasable.orErased(serialisedRequest);
  }

  public PriorAuthorityDraftStartedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      Instant occurredAt) {
    this(priorAuthorityId, applicationId, priorAuthorityType, schemaVersion, occurredAt, null);
  }

  /** Creates the event without draft content, as though the content had been erased. */
  public PriorAuthorityDraftStartedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      Instant occurredAt,
      String officeCode) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        schemaVersion,
        occurredAt,
        officeCode,
        Erasable.erased(),
        Erasable.erased());
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
