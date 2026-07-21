package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event marking that a caseworker was assigned to a work item. {@code allocationId} is the
 * namespaced aggregate/stream id; {@code workItemId} is the natural id of the allocated thing (an
 * application id or a prior authority id). The allocation carries no knowledge of which kind it is
 * and no personal data.
 */
public record CaseworkerAssignedEvent(
    UUID allocationId, UUID workItemId, UUID caseworkerId, Instant occurredAt) {}
