package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Persisted event emitted when a Prior Authority document type is set or replaced. */
@Event(namespace = "access", name = "PriorAuthorityDocumentTypeUpdated")
public record PriorAuthorityDocumentTypeUpdatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    Erasable<String> documentType,
    Instant occurredAt) {

  public PriorAuthorityDocumentTypeUpdatedEvent {
    documentType = Erasable.orErased(documentType);
  }

  /** Creates the event from plain content values. */
  public PriorAuthorityDocumentTypeUpdatedEvent(
      UUID priorAuthorityId, UUID documentId, String documentType, Instant occurredAt) {
    this(priorAuthorityId, documentId, Erasable.of(documentType), occurredAt);
  }
}
