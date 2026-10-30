package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Clears a linked group's shared work-item assignment. */
@Command(routingKey = "groupId")
public record UnassignLinkedGroupCommand(
    @TargetEntityId UUID groupId,
    UUID applicationId,
    long expectedAssignmentVersion,
    Instant occurredAt) {

  /** Validates the routed group, addressed application, and occurrence time. */
  public UnassignLinkedGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
