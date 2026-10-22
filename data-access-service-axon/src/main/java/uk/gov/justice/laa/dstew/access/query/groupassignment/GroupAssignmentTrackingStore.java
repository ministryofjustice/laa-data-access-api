package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One projection's private record of linked groups and of the assignment it shows for each
 * application.
 */
public interface GroupAssignmentTrackingStore {
  Optional<TrackedGroup> findGroup(UUID groupId);

  void saveGroup(TrackedGroup group);

  void deleteGroup(UUID groupId);

  Optional<TrackedApplication> findApplication(UUID applicationId);

  List<TrackedApplication> findApplicationsInGroup(UUID groupId);

  void saveApplication(TrackedApplication application);

  void deleteAll();
}
