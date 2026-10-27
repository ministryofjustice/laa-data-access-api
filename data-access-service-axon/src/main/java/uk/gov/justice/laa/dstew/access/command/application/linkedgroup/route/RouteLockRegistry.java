package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records the application-group routes locked by the current transaction so dispatch can verify
 * them.
 */
public final class RouteLockRegistry {
  private static final Object KEY = RouteLockRegistry.class;

  private RouteLockRegistry() {}

  private record LockedRoutes(Set<UUID> applicationIds, Set<UUID> groupIds) {}

  /** Records application routes locked (or verified absent) by the current transaction. */
  public static void recordApplications(Collection<UUID> applicationIds) {
    current().applicationIds().addAll(applicationIds);
  }

  /** Records that every route of {@code groupId} is locked by the current transaction. */
  public static void recordGroup(UUID groupId) {
    current().groupIds().add(groupId);
  }

  /** Returns the subset of {@code applicationIds} not locked by the current transaction. */
  public static Set<UUID> missingApplications(Collection<UUID> applicationIds) {
    var lockedRoutes = activeRoutes();
    var missing = new HashSet<>(applicationIds);
    if (lockedRoutes != null) {
      missing.removeAll(lockedRoutes.applicationIds());
    }
    return Set.copyOf(missing);
  }

  /** Returns whether every route of {@code groupId} is locked by the current transaction. */
  public static boolean holdsGroup(UUID groupId) {
    var lockedRoutes = activeRoutes();
    return lockedRoutes != null && lockedRoutes.groupIds().contains(groupId);
  }

  private static LockedRoutes current() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Route locks require an active transaction");
    }

    var existing = activeRoutes();
    if (existing != null) {
      return existing;
    }

    var lockedRoutes = new LockedRoutes(new HashSet<>(), new HashSet<>());
    TransactionSynchronizationManager.bindResource(KEY, lockedRoutes);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
          }
        });
    return lockedRoutes;
  }

  private static LockedRoutes activeRoutes() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      return null;
    }
    return (LockedRoutes) TransactionSynchronizationManager.getResource(KEY);
  }
}
