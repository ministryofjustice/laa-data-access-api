package uk.gov.justice.laa.dstew.access.command;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An event that relates to one or more root applications (its genesis). Every event implements
 * this, so a reader can find all events for an application without holding state, whichever
 * aggregate appended them. The IDs derive from the event's own fields.
 */
public interface GenesisScoped {

  /** Returns the root application IDs this event relates to; never empty. */
  Set<UUID> genesisIds();

  /** Returns when the event happened in the domain; history orders by this. */
  Instant occurredAt();

  /**
   * Collects IDs, dropping duplicates.
   *
   * @throws NullPointerException if any ID is null
   */
  static Set<UUID> ids(UUID... ids) {
    return Set.copyOf(new LinkedHashSet<>(Arrays.asList(ids)));
  }

  /**
   * Collects IDs from a list and further IDs, dropping duplicates.
   *
   * @throws NullPointerException if any ID is null
   */
  static Set<UUID> ids(Collection<UUID> some, UUID... more) {
    Set<UUID> all = new LinkedHashSet<>(some);
    all.addAll(Arrays.asList(more));
    return Set.copyOf(all);
  }
}
