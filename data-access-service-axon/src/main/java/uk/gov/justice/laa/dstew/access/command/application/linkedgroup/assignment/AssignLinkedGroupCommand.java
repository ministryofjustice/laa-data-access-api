package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Assigns a linked group's shared work-item assignment; {@code applicationId} is the work item the
 * caller addressed.
 */
@Command(routingKey = "groupId")
public record AssignLinkedGroupCommand(
    @TargetEntityId UUID groupId,
    UUID applicationId,
    UUID caseworkerId,
    long expectedAssignmentVersion,
    Instant occurredAt) {

  /** Validates the routed group, addressed application, caseworker, and occurrence time. */
  public AssignLinkedGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(caseworkerId, "caseworkerId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
