package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Removes a decided linked application from the group-owned active work set. */
@Command(routingKey = "groupId")
public record DeactivateLinkedGroupMemberWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID applicationId,
    long expectedMembershipVersion,
    Instant occurredAt) {}

