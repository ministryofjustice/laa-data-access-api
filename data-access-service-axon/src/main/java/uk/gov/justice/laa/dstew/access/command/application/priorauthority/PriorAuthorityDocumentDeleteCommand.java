package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Command that removes a document from a prior-authority draft. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentDeleteCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    Instant occurredAt,
    AuthenticatedCaller caller) {

  /** Temporary source-compatible constructor for non-HTTP command fixtures. */
  @Deprecated(forRemoval = true)
  public PriorAuthorityDocumentDeleteCommand(
      UUID priorAuthorityId, UUID documentId, Instant occurredAt) {
    this(priorAuthorityId, documentId, occurredAt, null);
  }
}
