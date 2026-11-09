package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
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
    Erasable<String> fileSuffix,
    Erasable<String> originalFilename)
    implements GenesisScoped {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public ApplicationDocumentUploadedEvent {
    documentType = Erasable.orErased(documentType);
    size = Erasable.orErased(size);
    contentType = Erasable.orErased(contentType);
    sourceService = Erasable.orErased(sourceService);
    fileSuffix = Erasable.orErased(fileSuffix);
    originalFilename = Erasable.orErased(originalFilename);
  }

  /** Creates the event from plain content values, including the original filename. */
  public ApplicationDocumentUploadedEvent(
      UUID applicationId,
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService,
      String fileSuffix,
      String originalFilename) {
    this(
        applicationId,
        documentId,
        Erasable.of(documentType),
        uploadedAt,
        Erasable.of(size),
        Erasable.of(contentType),
        checksum,
        Erasable.of(sourceService),
        Erasable.of(fileSuffix),
        Erasable.of(originalFilename));
  }

  /** Creates the event from plain content values without an original filename. */
  public ApplicationDocumentUploadedEvent(
      UUID applicationId,
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService,
      String fileSuffix) {
    this(
        applicationId,
        documentId,
        documentType,
        uploadedAt,
        size,
        contentType,
        checksum,
        sourceService,
        fileSuffix,
        (String) null);
  }

  /** Creates the legacy event shape without a recorded suffix. */
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
        (String) null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public Instant occurredAt() {
    return uploadedAt;
  }
}
