package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Establishes a linked application group from a lead and one joining application. */
@Command(routingKey = "groupId")
public record EstablishLinkedApplicationGroupCommand(
    @TargetEntityId UUID groupId,
    UUID leadApplicationId,
    UUID joiningApplicationId,
    Instant occurredAt,
    String justification) {

  /** Creates a command without justification. */
  public EstablishLinkedApplicationGroupCommand(
      UUID groupId, UUID leadApplicationId, UUID joiningApplicationId, Instant occurredAt) {
    this(groupId, leadApplicationId, joiningApplicationId, occurredAt, null);
  }

  /** Validates the routed group identity and the two initial members. */
  public EstablishLinkedApplicationGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(leadApplicationId, "leadApplicationId must not be null");
    Objects.requireNonNull(joiningApplicationId, "joiningApplicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (leadApplicationId.equals(joiningApplicationId)) {
      throw new IllegalArgumentException("joiningApplicationId must differ from leadApplicationId");
    }
  }

  /** Returns the initial members, lead first. */
  public List<UUID> memberApplicationIds() {
    return List.of(leadApplicationId, joiningApplicationId);
  }
}
