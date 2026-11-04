package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;

/**
 * Loads linked-group member application state inside the calling command handler's processing
 * context.
 */
public final class LinkedGroupMembers {
  private LinkedGroupMembers() {}

  /** Loads application state for distinct identifiers in ascending order. */
  public static SortedMap<UUID, ApplicationState> load(
      StateManager stateManager, Collection<UUID> applicationIds, ProcessingContext context) {
    var loaded = new TreeMap<UUID, ApplicationState>();
    for (var applicationId : new TreeSet<>(applicationIds)) {
      var aggregate =
          stateManager.loadEntity(ApplicationAggregate.class, applicationId, context).join();
      // Membership is only granted to applications validated by the route resolver, and
      // applications are never deleted, so a missing stream means a broken invariant.
      if (aggregate == null || aggregate.getApplicationId() == null) {
        throw new IllegalStateException(
            "Linked group member application " + applicationId + " has no event stream");
      }
      loaded.put(applicationId, aggregate.getState());
    }
    return loaded;
  }
}
