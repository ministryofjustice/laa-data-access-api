package uk.gov.justice.laa.dstew.access.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Identifies the independent data lifecycles a domain event concerns. ADS currently uses root
 * application IDs as genesis IDs. Readers can index event pointers into read-only lifecycle streams
 * without looking up current ownership or group membership.
 *
 * <p>The IDs derive from the event's own fields. Linking roots does not automatically include every
 * linked root in subsequent events.
 */
public interface GenesisScoped {

  /** Returns the root application IDs this event relates to; never empty. */
  Set<UUID> genesisIds();

  /**
   * Collects IDs, dropping duplicates.
   *
   * @throws IllegalArgumentException if no IDs are supplied
   * @throws NullPointerException if any ID is null
   */
  static Set<UUID> ids(UUID... ids) {
    return ids(Arrays.asList(ids));
  }

  /**
   * Collects IDs from a list and further IDs, dropping duplicates.
   *
   * @throws IllegalArgumentException if no IDs are supplied
   * @throws NullPointerException if any ID is null
   */
  static Set<UUID> ids(Collection<UUID> some, UUID... more) {
    Set<UUID> all = new LinkedHashSet<>(some);
    all.addAll(Arrays.asList(more));
    if (all.isEmpty()) {
      throw new IllegalArgumentException("At least one genesis ID is required");
    }
    return Set.copyOf(all);
  }
}
