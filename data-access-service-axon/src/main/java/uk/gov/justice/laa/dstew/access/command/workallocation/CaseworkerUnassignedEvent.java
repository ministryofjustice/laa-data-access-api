package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event marking that a work item's caseworker assignment was cleared. {@code allocationId}
 * is the namespaced aggregate/stream id; {@code workItemId} is the work item's natural id.
 */
public record CaseworkerUnassignedEvent(UUID allocationId, UUID workItemId, Instant occurredAt) {}
