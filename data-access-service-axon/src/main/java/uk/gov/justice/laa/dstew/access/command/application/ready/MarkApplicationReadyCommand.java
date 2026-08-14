package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Records that automatic assessment completed without an automatic grant. */
@Command(routingKey = "applicationId")
public record MarkApplicationReadyCommand(
    @TargetEntityId UUID applicationId,
    Long expectedApplicationVersion,
    String serialisedRequest,
    Instant occurredAt) {

  /** Creates a version-independent command for the auto-grant outcome endpoint. */
  public MarkApplicationReadyCommand(
      UUID applicationId, String serialisedRequest, Instant occurredAt) {
    this(applicationId, null, serialisedRequest, occurredAt);
  }
}
