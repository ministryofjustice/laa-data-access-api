package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Adds one application to an already-established linked group. */
@Command(routingKey = "groupId")
public record AddApplicationToLinkedGroupCommand(
    @TargetEntityId UUID groupId, UUID applicationId, Instant occurredAt) {

  /** Validates the routed group identity and application identifier. */
  public AddApplicationToLinkedGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
