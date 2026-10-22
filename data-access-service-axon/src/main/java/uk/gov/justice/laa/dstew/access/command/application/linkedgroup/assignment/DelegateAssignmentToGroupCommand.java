package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Records that a linked group now owns this application's assignment and returns what the
 * application hands over.
 */
@Command(routingKey = "applicationId")
public record DelegateAssignmentToGroupCommand(
    @TargetEntityId UUID applicationId, UUID groupId, Instant occurredAt) {

  /** Validates the application, group, and occurrence time. */
  public DelegateAssignmentToGroupCommand {
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
