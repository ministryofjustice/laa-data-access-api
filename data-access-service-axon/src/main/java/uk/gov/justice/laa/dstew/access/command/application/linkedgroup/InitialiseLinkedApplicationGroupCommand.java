package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Initialises a {@code LinkedApplicationGroupAggregate}. The group ID equals the lead application's
 * ID, so the two aggregates share the same identifier but have different stream types.
 */
@Command(routingKey = "groupId")
public record InitialiseLinkedApplicationGroupCommand(
    @TargetEntityId UUID groupId,
    UUID leadApplicationId,
    List<UUID> memberApplicationIds,
    Instant occurredAt) {}
