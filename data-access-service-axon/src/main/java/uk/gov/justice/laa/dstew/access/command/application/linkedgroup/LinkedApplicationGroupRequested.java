package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emitted by the lead {@code ApplicationAggregate} when it acknowledges a group-formation request.
 * An event handler picks this up and dispatches {@link InitialiseLinkedApplicationGroupCommand}.
 */
public record LinkedApplicationGroupRequested(
    UUID groupId, UUID leadApplicationId, List<UUID> memberApplicationIds, Instant occurredAt) {}
