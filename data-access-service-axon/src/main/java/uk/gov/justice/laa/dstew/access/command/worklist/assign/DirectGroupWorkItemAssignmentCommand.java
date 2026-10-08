package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Overwrite-style assignment used to propagate a caseworker to every member of a linked application
 * group.
 *
 * <p>Unlike {@link DirectWorkItemAssignmentCommand}, this command carries no expected-assignment
 * version and is handled idempotently: the dispatching use case already holds a pessimistic lock on
 * every route in the group, so that lock - not a per-item optimistic version - is the concurrency
 * guard for this operation.
 */
@Command(routingKey = "workItemId")
public record DirectGroupWorkItemAssignmentCommand(
    @TargetEntityId UUID workItemId,
    UUID caseworkerId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
