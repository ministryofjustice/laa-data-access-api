package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Persisted event emitted after an application document upload completes. */
@Event(namespace = "access", name = "ApplicationDocumentUploaded")
public record ApplicationDocumentUploadedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    UUID documentId,
    Erasable<String> documentType,
    Instant uploadedAt,
    Erasable<Long> size,
    Erasable<String> contentType,
    String checksum,
    Erasable<String> sourceService,
    Long applicationDataVersion,
    Erasable<String> fileSuffix) {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public ApplicationDocumentUploadedEvent {
    documentType = Erasable.orErased(documentType);
    size = Erasable.orErased(size);
    contentType = Erasable.orErased(contentType);
    sourceService = Erasable.orErased(sourceService);
    fileSuffix = Erasable.orErased(fileSuffix);
  }

  /** Creates the event from plain content values. */
  public ApplicationDocumentUploadedEvent(
      UUID applicationId,
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService,
      Long applicationDataVersion,
      String fileSuffix) {
    this(
        applicationId,
        documentId,
        Erasable.of(documentType),
        uploadedAt,
        Erasable.of(size),
        Erasable.of(contentType),
        checksum,
        Erasable.of(sourceService),
        applicationDataVersion,
        Erasable.of(fileSuffix));
  }

  /** Creates the historical event shape with a data-version pointer but no recorded suffix. */
  public ApplicationDocumentUploadedEvent(
      UUID applicationId,
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService,
      Long applicationDataVersion) {
    this(
        applicationId,
        documentId,
        documentType,
        uploadedAt,
        size,
        contentType,
        checksum,
        sourceService,
        applicationDataVersion,
        (String) null);
  }

  /** Creates the legacy event shape without a sensitive-data version pointer. */
  public ApplicationDocumentUploadedEvent(
      UUID applicationId,
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService) {
    this(
        applicationId,
        documentId,
        documentType,
        uploadedAt,
        size,
        contentType,
        checksum,
        sourceService,
        (Long) null);
  }
}
