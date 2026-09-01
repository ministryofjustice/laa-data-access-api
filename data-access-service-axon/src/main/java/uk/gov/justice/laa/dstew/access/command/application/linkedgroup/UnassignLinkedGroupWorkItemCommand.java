package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Internal command returning all active linked-group members to the open work list. */
@Command(routingKey = "groupId")
public record UnassignLinkedGroupWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID selectedApplicationId,
    long expectedMembershipVersion,
    long expectedAssignmentVersion,
    Instant occurredAt) {}

