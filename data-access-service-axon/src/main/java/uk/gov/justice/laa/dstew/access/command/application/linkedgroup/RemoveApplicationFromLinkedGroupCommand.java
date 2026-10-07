package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Removes an application from an established linked group. */
@Command(routingKey = "groupId")
public record RemoveApplicationFromLinkedGroupCommand(
    @TargetEntityId UUID groupId,
    UUID applicationId,
    long expectedGroupVersion,
    Instant occurredAt) {

  /** Validates the routed group identity and application identifier. */
  public RemoveApplicationFromLinkedGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
