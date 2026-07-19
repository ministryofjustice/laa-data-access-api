package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Initialises a {@code LinkedApplicationGroupAggregate}. The group ID equals the lead application's
 * ID, so the two aggregates share the same identifier but have different stream types.
 */
public record InitialiseLinkedApplicationGroupCommand(
    @TargetAggregateIdentifier UUID groupId,
    UUID leadApplicationId,
    List<UUID> memberApplicationIds,
    Instant occurredAt) {}
