package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** In-memory tracking store for deterministic tracker unit tests. */
final class InMemoryGroupAssignmentTrackingStore implements GroupAssignmentTrackingStore {
  private final TreeMap<UUID, TrackedGroup> groups = new TreeMap<>();
  private final TreeMap<UUID, TrackedApplication> applications = new TreeMap<>();

  @Override
  public Optional<TrackedGroup> findGroup(UUID groupId) {
    return Optional.ofNullable(groups.get(groupId));
  }

  @Override
  public void saveGroup(TrackedGroup group) {
    groups.put(group.groupId(), group);
  }

  @Override
  public void deleteGroup(UUID groupId) {
    groups.remove(groupId);
  }

  @Override
  public Optional<TrackedApplication> findApplication(UUID applicationId) {
    return Optional.ofNullable(applications.get(applicationId));
  }

  @Override
  public List<TrackedApplication> findApplicationsInGroup(UUID groupId) {
    return applications.values().stream()
        .filter(application -> groupId.equals(application.groupId()))
        .toList();
  }

  @Override
  public void saveApplication(TrackedApplication application) {
    applications.put(application.applicationId(), application);
  }

  @Override
  public void deleteAll() {
    applications.clear();
    groups.clear();
  }
}
