package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Changes the lead of an established linked application group. */
@Command(routingKey = "groupId")
public record ChangeLinkedGroupLeadCommand(
    @TargetEntityId UUID groupId,
    UUID newLeadApplicationId,
    long expectedGroupVersion,
    Instant occurredAt) {

  /** Validates the routed group identity and new lead. */
  public ChangeLinkedGroupLeadCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(newLeadApplicationId, "newLeadApplicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
