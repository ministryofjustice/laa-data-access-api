package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import org.jspecify.annotations.Nullable;

/** Records that automatic assessment completed without an automatic grant. */
@Command(routingKey = "applicationId")
public record MarkApplicationReadyCommand(
    @TargetEntityId UUID applicationId,
    Long expectedApplicationVersion,
    String serialisedRequest,
    Instant occurredAt,
    @Nullable UUID linkedGroupId) {

  /** Creates a command without a linked-group route. */
  public MarkApplicationReadyCommand(
      UUID applicationId,
      Long expectedApplicationVersion,
      String serialisedRequest,
      Instant occurredAt) {
    this(applicationId, expectedApplicationVersion, serialisedRequest, occurredAt, null);
  }

  /** Creates a version-independent command for the auto-grant outcome endpoint. */
  public MarkApplicationReadyCommand(
      UUID applicationId, String serialisedRequest, Instant occurredAt) {
    this(applicationId, null, serialisedRequest, occurredAt, null);
  }

  /** Returns this command routed with the supplied linked-group ID. */
  public MarkApplicationReadyCommand withLinkedGroupId(@Nullable UUID linkedGroupId) {
    return new MarkApplicationReadyCommand(
        applicationId, expectedApplicationVersion, serialisedRequest, occurredAt, linkedGroupId);
  }
}
