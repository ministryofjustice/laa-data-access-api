package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One projection's private per-application record of group and displayed assignment state. */
public interface GroupAssignmentTrackingStore {
  Optional<TrackedApplication> findApplication(UUID applicationId);

  /** Returns members in ascending application-ID order. */
  List<TrackedApplication> findApplicationsInGroup(UUID groupId);

  void saveApplication(TrackedApplication application);

  void deleteAll();
}
