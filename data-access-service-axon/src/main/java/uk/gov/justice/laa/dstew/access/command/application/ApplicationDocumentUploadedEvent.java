package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Persisted event emitted after an application document upload completes. */
@Event
public record ApplicationDocumentUploadedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService,
    Long applicationDataVersion,
    String fileSuffix)
    implements GenesisScoped {

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
        null);
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
        null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
