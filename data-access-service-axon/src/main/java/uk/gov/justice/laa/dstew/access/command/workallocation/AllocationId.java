package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives the {@link WorkAllocation} aggregate identifier from a work item's natural id.
 *
 * <p>An event store keys streams by aggregate identifier alone (the aggregate <i>type</i> is not
 * part of the stream key). An application work item is identified by the application's own id, so a
 * {@code WorkAllocation} keyed directly by {@code workItemId} would share a stream with the {@code
 * Application} aggregate and collide on append. Namespacing the id into a distinct, deterministic
 * UUID keeps the allocation on its own stream while remaining derivable without a lookup.
 */
public final class AllocationId {

  private AllocationId() {}

  /** Returns the deterministic allocation-stream id for the given work item. */
  public static UUID forWorkItem(UUID workItemId) {
    return UUID.nameUUIDFromBytes(
        ("work-allocation:" + workItemId).getBytes(StandardCharsets.UTF_8));
  }
}
