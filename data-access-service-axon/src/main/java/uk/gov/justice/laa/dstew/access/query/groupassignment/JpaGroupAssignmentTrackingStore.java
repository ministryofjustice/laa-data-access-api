package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA implementation shared by the four independently-owned tracking tables. */
public abstract class JpaGroupAssignmentTrackingStore<A extends GroupAssignmentApplicationRow>
    implements GroupAssignmentTrackingStore {
  private final GroupAssignmentApplicationRowRepository<A> applicationRows;

  protected JpaGroupAssignmentTrackingStore(
      GroupAssignmentApplicationRowRepository<A> applicationRows) {
    this.applicationRows = applicationRows;
  }

  protected abstract A newApplicationRow();

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
  }
}
