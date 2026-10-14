package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Command that sets or replaces the type of an uploaded Prior Authority document. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentTypeUpdateCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String documentType,
    String serialisedRequest,
    Instant occurredAt,
    AuthenticatedCaller caller) {

  /** Temporary source-compatible constructor for non-HTTP command fixtures. */
  @Deprecated(forRemoval = true)
  public PriorAuthorityDocumentTypeUpdateCommand(
      UUID priorAuthorityId,
      UUID documentId,
      String documentType,
      String serialisedRequest,
      Instant occurredAt) {
    this(priorAuthorityId, documentId, documentType, serialisedRequest, occurredAt, null);
  }
}
