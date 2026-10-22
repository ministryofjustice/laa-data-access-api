package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import org.jspecify.annotations.Nullable;

/** Returns ownership of the assignment to an application leaving a linked group. */
@Command(routingKey = "applicationId")
public record ReclaimAssignmentFromGroupCommand(
    @TargetEntityId UUID applicationId,
    UUID groupId,
    @Nullable UUID groupCaseworkerId,
    long groupAssignmentVersion,
    Instant occurredAt) {

  /** Validates the application, group, and occurrence time. */
  public ReclaimAssignmentFromGroupCommand {
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
