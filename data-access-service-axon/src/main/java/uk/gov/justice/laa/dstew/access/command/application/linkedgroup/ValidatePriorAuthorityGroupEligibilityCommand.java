package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Validates that an application is currently the lead of its linked application group. */
@Command(routingKey = "groupId")
public record ValidatePriorAuthorityGroupEligibilityCommand(
    @TargetEntityId UUID groupId, UUID applicationId) {

  /** Validates the routed group and application identities. */
  public ValidatePriorAuthorityGroupEligibilityCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(applicationId, "applicationId must not be null");
  }
}
