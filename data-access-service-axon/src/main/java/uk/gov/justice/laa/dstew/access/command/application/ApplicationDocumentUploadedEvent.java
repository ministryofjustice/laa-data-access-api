package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Persisted event emitted after an application document upload completes. */
@Event(namespace = "access", name = "ApplicationDocumentUploaded")
public record ApplicationDocumentUploadedEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField UUID documentId,
    @Nullable String documentType,
    @RetainedField Instant uploadedAt,
    @Nullable Long size,
    @Nullable String contentType,
    @RetainedField(nullable = true) String checksum,
    @Nullable String sourceService,
    @RetainedField(nullable = true) Long applicationDataVersion,
    @Nullable String fileSuffix,
    @Nullable String originalFilename)
    implements GenesisScoped {

  /** Creates the event without an original filename. */
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
        documentType,
        uploadedAt,
        size,
        contentType,
        checksum,
        sourceService,
        applicationDataVersion,
        fileSuffix,
        null);
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

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public Instant occurredAt() {
    return uploadedAt;
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
