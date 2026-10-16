package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Command that starts a new Prior Authority draft in progress. */
@Command(routingKey = "priorAuthorityId")
public record CreatePriorAuthorityDraftCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID applicationId,
    PriorAuthorityContent content,
    String serialisedRequest,
    int schemaVersion,
    String schemaName,
    Instant occurredAt,
    AuthenticatedCaller caller) {

  /** Temporary source-compatible constructor for non-HTTP command fixtures. */
  @Deprecated(forRemoval = true)
  public CreatePriorAuthorityDraftCommand(
      UUID priorAuthorityId,
      UUID applicationId,
      PriorAuthorityContent content,
      String serialisedRequest,
      int schemaVersion,
      String schemaName,
      Instant occurredAt) {
    this(
        priorAuthorityId,
        applicationId,
        content,
        serialisedRequest,
        schemaVersion,
        schemaName,
        occurredAt,
        null);
  }
}
