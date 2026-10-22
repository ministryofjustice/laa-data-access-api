package uk.gov.justice.laa.dstew.access.query.groupassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionAssignmentApplicationRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionAssignmentGroupRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionGroupAssignmentStore;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryAssignmentApplicationRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryAssignmentGroupRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryGroupAssignmentStore;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexAssignmentApplicationRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexAssignmentGroupRowRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexGroupAssignmentStore;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListAssignmentApplicationRow;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListAssignmentApplicationRowRepository;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListAssignmentGroupRow;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListAssignmentGroupRowRepository;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListGroupAssignmentStore;

@ExtendWith(MockitoExtension.class)
class JpaGroupAssignmentTrackingStoreTest {
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID APPLICATION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CASEWORKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Mock private WorkListAssignmentGroupRowRepository groupRows;
  @Mock private WorkListAssignmentApplicationRowRepository applicationRows;
  @Mock private ApplicationProjectionAssignmentGroupRowRepository projectionGroupRows;
  @Mock private ApplicationProjectionAssignmentApplicationRowRepository projectionApplicationRows;
  @Mock private ApplicationListIndexAssignmentGroupRowRepository indexGroupRows;
  @Mock private ApplicationListIndexAssignmentApplicationRowRepository indexApplicationRows;
  @Mock private ApplicationHistoryAssignmentGroupRowRepository historyGroupRows;
  @Mock private ApplicationHistoryAssignmentApplicationRowRepository historyApplicationRows;

  private WorkListGroupAssignmentStore store;

  @BeforeEach
  void setUp() {
    store = new WorkListGroupAssignmentStore(groupRows, applicationRows);
  }

  @Test
  void givenNoGroupRow_whenSaved_thenInsertsRowWithTrackedValues() {
    TrackedGroup group = new TrackedGroup(GROUP_ID, CASEWORKER_ID, 4L);
    when(groupRows.findById(GROUP_ID)).thenReturn(Optional.empty());

    store.saveGroup(group);

    ArgumentCaptor<WorkListAssignmentGroupRow> captor =
        ArgumentCaptor.forClass(WorkListAssignmentGroupRow.class);
    verify(groupRows).save(captor.capture());
    assertThat(captor.getValue().toTrackedGroup()).isEqualTo(group);
  }

  @Test
  void givenExistingApplicationRow_whenSaved_thenUpdatesThatRow() {
    WorkListAssignmentApplicationRow row = new WorkListAssignmentApplicationRow();
    row.copyFrom(new TrackedApplication(APPLICATION_ID, null, false, null, 0L));
    TrackedApplication updated =
        new TrackedApplication(APPLICATION_ID, GROUP_ID, true, CASEWORKER_ID, 8L);
    when(applicationRows.findById(APPLICATION_ID)).thenReturn(Optional.of(row));

    store.saveApplication(updated);

    verify(applicationRows).save(row);
    assertThat(row.toTrackedApplication()).isEqualTo(updated);
  }

  @Test
  void givenGroupRow_whenFound_thenMapsToTrackedGroup() {
    WorkListAssignmentGroupRow row = new WorkListAssignmentGroupRow();
    TrackedGroup expected = new TrackedGroup(GROUP_ID, CASEWORKER_ID, 3L);
    row.copyFrom(expected);
    when(groupRows.findById(GROUP_ID)).thenReturn(Optional.of(row));

    assertThat(store.findGroup(GROUP_ID)).contains(expected);
  }

  @Test
  void givenGroupMembers_whenFound_thenMappedInRepositoryOrder() {
    WorkListAssignmentApplicationRow first = new WorkListAssignmentApplicationRow();
    WorkListAssignmentApplicationRow second = new WorkListAssignmentApplicationRow();
    UUID firstId = new UUID(0L, 1L);
    UUID secondId = new UUID(0L, 2L);
    first.copyFrom(new TrackedApplication(firstId, GROUP_ID, true, null, 0L));
    second.copyFrom(new TrackedApplication(secondId, GROUP_ID, true, CASEWORKER_ID, 1L));
    when(applicationRows.findAllByGroupIdOrderByApplicationId(GROUP_ID))
        .thenReturn(List.of(first, second));

    assertThat(store.findApplicationsInGroup(GROUP_ID))
        .containsExactly(first.toTrackedApplication(), second.toTrackedApplication());
  }

  @Test
  void givenGroup_whenDeleted_thenDeletedById() {
    store.deleteGroup(GROUP_ID);

    verify(groupRows).deleteById(GROUP_ID);
  }

  @Test
  void givenStore_whenDeleteAll_thenClearsBothTables() {
    store.deleteAll();

    verify(applicationRows).deleteAllInBatch();
    verify(groupRows).deleteAllInBatch();
  }

  @Test
  void givenApplicationProjectionStore_whenSaving_thenCreatesItsProjectionRows() {
    var projectionStore =
        new ApplicationProjectionGroupAssignmentStore(
            projectionGroupRows, projectionApplicationRows);
    projectionStore.saveGroup(new TrackedGroup(GROUP_ID, CASEWORKER_ID, 1L));
    projectionStore.saveApplication(
        new TrackedApplication(APPLICATION_ID, GROUP_ID, true, CASEWORKER_ID, 1L));

    verify(projectionGroupRows).save(any());
    verify(projectionApplicationRows).save(any());
  }

  @Test
  void givenListIndexStore_whenSaving_thenCreatesItsProjectionRows() {
    var indexStore =
        new ApplicationListIndexGroupAssignmentStore(indexGroupRows, indexApplicationRows);
    indexStore.saveGroup(new TrackedGroup(GROUP_ID, CASEWORKER_ID, 1L));
    indexStore.saveApplication(
        new TrackedApplication(APPLICATION_ID, GROUP_ID, true, CASEWORKER_ID, 1L));

    verify(indexGroupRows).save(any());
    verify(indexApplicationRows).save(any());
  }

  @Test
  void givenHistoryStore_whenSaving_thenCreatesItsProjectionRows() {
    var historyStore =
        new ApplicationHistoryGroupAssignmentStore(historyGroupRows, historyApplicationRows);
    historyStore.saveGroup(new TrackedGroup(GROUP_ID, CASEWORKER_ID, 1L));
    historyStore.saveApplication(
        new TrackedApplication(APPLICATION_ID, GROUP_ID, true, CASEWORKER_ID, 1L));

    verify(historyGroupRows).save(any());
    verify(historyApplicationRows).save(any());
  }
}
