package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Internal command assigning all active members through their shared group boundary. */
@Command(routingKey = "groupId")
public record AssignLinkedGroupWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID selectedApplicationId,
    long expectedMembershipVersion,
    long expectedAssignmentVersion,
    UUID caseworkerId,
    Instant occurredAt) {}

