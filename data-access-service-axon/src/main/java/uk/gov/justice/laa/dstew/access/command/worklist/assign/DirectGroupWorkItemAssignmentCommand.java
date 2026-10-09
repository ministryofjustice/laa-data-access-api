package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Overwrite-style assignment used to propagate a caseworker to every member of a linked application
 * group.
 *
 * <p>The dispatching use case holds a pessimistic lock across the whole group as the concurrency
 * guard, so most members are assigned without a per-item version check, even if their stored
 * version has moved on.
 *
 * <p>The exception is {@code expectedAssignmentVersion}, populated only for the item the caseworker
 * was viewing when they submitted the request. That field is still enforced, so a stale read of
 * that item is rejected exactly as it would be for a standalone assignment.
 */
@Command(routingKey = "workItemId")
public record DirectGroupWorkItemAssignmentCommand(
    @TargetEntityId UUID workItemId,
    UUID caseworkerId,
    Long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
