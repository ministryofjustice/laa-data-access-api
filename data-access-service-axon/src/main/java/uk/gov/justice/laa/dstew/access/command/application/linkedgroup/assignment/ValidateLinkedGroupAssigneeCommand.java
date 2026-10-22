package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Checks, without appending events, that the caseworker is the assignee of the group that owns the
 * application's assignment.
 */
@Command(routingKey = "groupId")
public record ValidateLinkedGroupAssigneeCommand(
    @TargetEntityId UUID groupId, UUID applicationId, UUID caseworkerId) {

  /** Validates the routed group, application, and supplied caseworker. */
  public ValidateLinkedGroupAssigneeCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(caseworkerId, "caseworkerId must not be null");
  }
}
