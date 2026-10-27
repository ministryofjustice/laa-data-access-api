package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RouteLockRegistryTest {
  @BeforeEach
  void setUpTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  @AfterEach
  void clearTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
    }
    TransactionSynchronizationManager.clear();
  }

  @Test
  void givenRecordedLocks_whenChecked_thenReportsApplicationsAndGroups() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();

    RouteLockRegistry.recordApplications(List.of(applicationId));
    RouteLockRegistry.recordGroup(groupId);

    assertThat(RouteLockRegistry.missingApplications(List.of(applicationId))).isEmpty();
    assertThat(RouteLockRegistry.holdsGroup(groupId)).isTrue();
  }

  @Test
  void givenPartiallyRecordedApplications_whenChecked_thenReturnsOnlyMissingIds() {
    UUID lockedId = UUID.randomUUID();
    UUID missingId = UUID.randomUUID();
    RouteLockRegistry.recordApplications(List.of(lockedId));

    assertThat(RouteLockRegistry.missingApplications(List.of(lockedId, missingId)))
        .containsExactly(missingId);
  }

  @Test
  void givenNoActiveTransaction_whenRecording_thenThrowsAndReportsNothingHeld() {
    TransactionSynchronizationManager.clear();
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();

    assertThatThrownBy(() -> RouteLockRegistry.recordApplications(List.of(applicationId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Route locks require an active transaction");
    assertThatThrownBy(() -> RouteLockRegistry.recordGroup(groupId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Route locks require an active transaction");
    assertThat(RouteLockRegistry.holdsGroup(groupId)).isFalse();
    assertThat(RouteLockRegistry.missingApplications(List.of(applicationId)))
        .containsExactly(applicationId);
  }

  @Test
  void givenNoSynchronization_whenRecording_thenRequiresSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();

    assertThatThrownBy(() -> RouteLockRegistry.recordApplications(List.of(applicationId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Route locks require an active transaction");
    assertThat(RouteLockRegistry.holdsGroup(groupId)).isFalse();
    assertThat(RouteLockRegistry.missingApplications(List.of(applicationId)))
        .containsExactly(applicationId);
  }

  @Test
  void givenActiveTransactionWithoutRecordedGroup_whenChecked_thenDoesNotHoldGroup() {
    UUID groupId = UUID.randomUUID();

    assertThat(RouteLockRegistry.holdsGroup(groupId)).isFalse();
  }

  @Test
  void givenTransactionCompleted_whenChecked_thenNoLocksRemain() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    RouteLockRegistry.recordApplications(List.of(applicationId));
    RouteLockRegistry.recordGroup(groupId);

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

    assertThat(TransactionSynchronizationManager.getResource(RouteLockRegistry.class)).isNull();
    assertThat(RouteLockRegistry.missingApplications(List.of(applicationId)))
        .containsExactly(applicationId);
    assertThat(RouteLockRegistry.holdsGroup(groupId)).isFalse();
  }
}
