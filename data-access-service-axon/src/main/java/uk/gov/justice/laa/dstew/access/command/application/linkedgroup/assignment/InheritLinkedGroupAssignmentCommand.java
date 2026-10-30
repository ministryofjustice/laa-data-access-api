package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Records that a member that has just become ready for manual assessment takes the group's current
 * caseworker.
 */
@Command(routingKey = "groupId")
public record InheritLinkedGroupAssignmentCommand(
    @TargetEntityId UUID groupId, UUID applicationId, Instant occurredAt) {

  /** Validates the routed group, member application, and occurrence time. */
  public InheritLinkedGroupAssignmentCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
