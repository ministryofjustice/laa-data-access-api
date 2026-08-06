package uk.gov.justice.laa.dstew.access.command.workitem;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reversible identifier mapping that keeps WorkItem streams distinct from Application streams. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkItemId {

  private static final long MASK = 1L << 62;

  public static UUID toAggregateId(UUID itemId) {
    return new UUID(itemId.getMostSignificantBits() ^ MASK, itemId.getLeastSignificantBits());
  }

  public static UUID toItemId(UUID aggregateId) {
    return new UUID(aggregateId.getMostSignificantBits() ^ MASK, aggregateId.getLeastSignificantBits());
  }
}
