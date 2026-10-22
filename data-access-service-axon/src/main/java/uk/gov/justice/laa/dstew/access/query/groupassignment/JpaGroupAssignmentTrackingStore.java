package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA implementation shared by the four independently-owned tracking table pairs. */
public abstract class JpaGroupAssignmentTrackingStore<
        G extends GroupAssignmentGroupRow, A extends GroupAssignmentApplicationRow>
    implements GroupAssignmentTrackingStore {
  private final GroupAssignmentGroupRowRepository<G> groupRows;
  private final GroupAssignmentApplicationRowRepository<A> applicationRows;

  protected JpaGroupAssignmentTrackingStore(
      GroupAssignmentGroupRowRepository<G> groupRows,
      GroupAssignmentApplicationRowRepository<A> applicationRows) {
    this.groupRows = groupRows;
    this.applicationRows = applicationRows;
  }

  protected abstract G newGroupRow();

  protected abstract A newApplicationRow();

  @Override
  public Optional<TrackedGroup> findGroup(UUID groupId) {
    return groupRows.findById(groupId).map(GroupAssignmentGroupRow::toTrackedGroup);
  }

  @Override
  public void saveGroup(TrackedGroup group) {
    G row = groupRows.findById(group.groupId()).orElseGet(this::newGroupRow);
    row.copyFrom(group);
    groupRows.save(row);
  }

  @Override
  public void deleteGroup(UUID groupId) {
    groupRows.deleteById(groupId);
  }

  @Override
  public Optional<TrackedApplication> findApplication(UUID applicationId) {
    return applicationRows
        .findById(applicationId)
        .map(GroupAssignmentApplicationRow::toTrackedApplication);
  }

  @Override
  public List<TrackedApplication> findApplicationsInGroup(UUID groupId) {
    return applicationRows.findAllByGroupIdOrderByApplicationId(groupId).stream()
        .map(GroupAssignmentApplicationRow::toTrackedApplication)
        .toList();
  }

  @Override
  public void saveApplication(TrackedApplication application) {
    A row =
        applicationRows.findById(application.applicationId()).orElseGet(this::newApplicationRow);
    row.copyFrom(application);
    applicationRows.save(row);
  }

  @Override
  public void deleteAll() {
    applicationRows.deleteAllInBatch();
    groupRows.deleteAllInBatch();
  }
}
