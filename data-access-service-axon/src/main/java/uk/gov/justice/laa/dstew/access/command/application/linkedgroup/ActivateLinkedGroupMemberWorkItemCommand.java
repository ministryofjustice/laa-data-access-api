package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Activates one eligible application member at its linked-group assignment boundary. */
@Command(routingKey = "groupId")
public record ActivateLinkedGroupMemberWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID applicationId,
    long expectedMembershipVersion,
    Instant occurredAt) {}
