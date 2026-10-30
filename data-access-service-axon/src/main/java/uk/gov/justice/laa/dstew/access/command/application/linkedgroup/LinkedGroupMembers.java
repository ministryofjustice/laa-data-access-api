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
      loaded.put(
          applicationId,
          aggregate == null ? ApplicationState.empty(applicationId) : aggregate.getState());
    }
    return loaded;
  }
}
