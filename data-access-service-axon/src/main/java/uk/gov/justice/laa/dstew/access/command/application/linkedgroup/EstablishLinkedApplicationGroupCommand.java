package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Establishes the initial membership of a linked application group. */
@Command(routingKey = "groupId")
public record EstablishLinkedApplicationGroupCommand(
    @TargetEntityId UUID groupId,
    UUID leadApplicationId,
    List<UUID> memberApplicationIds,
    Instant occurredAt) {

  /** Validates the routed group identity and initial member list. */
  public EstablishLinkedApplicationGroupCommand {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(leadApplicationId, "leadApplicationId must not be null");
    Objects.requireNonNull(memberApplicationIds, "memberApplicationIds must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");

    memberApplicationIds = List.copyOf(memberApplicationIds);
    memberApplicationIds.forEach(
        memberApplicationId ->
            Objects.requireNonNull(
                memberApplicationId, "memberApplicationIds must not contain null values"));

    if (memberApplicationIds.isEmpty()) {
      throw new IllegalArgumentException("memberApplicationIds must not be empty");
    }
    if (!memberApplicationIds.contains(leadApplicationId)) {
      throw new IllegalArgumentException(
          "memberApplicationIds must contain lead application " + leadApplicationId);
    }
    if (memberApplicationIds.size() != new LinkedHashSet<>(memberApplicationIds).size()) {
      throw new IllegalArgumentException("memberApplicationIds must not contain duplicate values");
    }
  }
}
