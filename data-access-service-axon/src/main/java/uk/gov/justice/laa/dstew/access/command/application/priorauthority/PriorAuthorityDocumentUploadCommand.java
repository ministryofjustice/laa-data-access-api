package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Command that records a prior-authority document after SDS accepts its content. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentUploadCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String sourceService,
    String checksum,
    Instant occurredAt,
    String originalFilename,
    Long fileSize,
    String contentType,
    AuthenticatedCaller caller) {

  /** Temporary source-compatible constructor for non-HTTP command fixtures. */
  @Deprecated(forRemoval = true)
  public PriorAuthorityDocumentUploadCommand(
      UUID priorAuthorityId,
      UUID documentId,
      String sourceService,
      String checksum,
      Instant occurredAt,
      String originalFilename,
      Long fileSize,
      String contentType) {
    this(
        priorAuthorityId,
        documentId,
        sourceService,
        checksum,
        occurredAt,
        originalFilename,
        fileSize,
        contentType,
        null);
  }
}
