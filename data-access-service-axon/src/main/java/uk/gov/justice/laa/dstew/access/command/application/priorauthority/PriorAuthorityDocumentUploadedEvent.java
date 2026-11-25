package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Persisted event emitted when a prior-authority document is uploaded and finalised. */
@Event(namespace = "access", name = "PriorAuthorityDocumentUploaded")
public record PriorAuthorityDocumentUploadedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID documentId,
    @RetainedField Instant uploadedAt,
    @Nullable Long size,
    @Nullable String contentType,
    @RetainedField String checksum,
    @RetainedField UUID parentApplicationId,
    @Nullable String sourceService,
    @Nullable String fileSuffix,
    @Nullable String originalFilename)
    implements GenesisScoped {

  /** Creates the upload event without a recorded original filename. */
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
        size,
        contentType,
        checksum,
        parentApplicationId,
        sourceService,
        fileSuffix,
        null);
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

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(parentApplicationId);
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
