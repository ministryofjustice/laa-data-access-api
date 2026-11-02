package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Persisted event emitted when a prior-authority document is uploaded and finalised. */
@Event(namespace = "access", name = "PriorAuthorityDocumentUploaded")
public record PriorAuthorityDocumentUploadedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    Instant uploadedAt,
    Erasable<Long> size,
    Erasable<String> contentType,
    String checksum,
    UUID parentApplicationId,
    Erasable<String> sourceService,
    Erasable<String> fileSuffix) {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public PriorAuthorityDocumentUploadedEvent {
    size = Erasable.orErased(size);
    contentType = Erasable.orErased(contentType);
    sourceService = Erasable.orErased(sourceService);
    fileSuffix = Erasable.orErased(fileSuffix);
  }

  /** Creates the event from plain content values. */
  public PriorAuthorityDocumentUploadedEvent(
      UUID priorAuthorityId,
      UUID documentId,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      UUID parentApplicationId,
      String sourceService,
      String fileSuffix) {
    this(
        priorAuthorityId,
        documentId,
        uploadedAt,
        Erasable.of(size),
        Erasable.of(contentType),
        checksum,
        parentApplicationId,
        Erasable.of(sourceService),
        Erasable.of(fileSuffix));
  }

  /** Creates the historical upload event whose storage suffix is unknown. */
  public PriorAuthorityDocumentUploadedEvent(
      UUID priorAuthorityId,
      UUID documentId,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      UUID parentApplicationId,
      String sourceService) {
    this(
        priorAuthorityId,
        documentId,
        uploadedAt,
        size,
        contentType,
        checksum,
        parentApplicationId,
        sourceService,
        (String) null);
  }
}
