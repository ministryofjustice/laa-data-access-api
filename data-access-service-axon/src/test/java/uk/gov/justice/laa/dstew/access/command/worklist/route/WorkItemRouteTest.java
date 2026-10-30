package uk.gov.justice.laa.dstew.access.command.worklist.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class WorkItemRouteTest {
  private static final Instant CREATED_AT = Instant.parse("2026-09-14T09:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-09-14T10:00:00Z");

  @Test
  void givenStandaloneRoute_whenJoined_thenRoutesToGroup() {
    UUID workItemId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkItemRoute route = route(workItemId, WorkItemRouteKind.STANDALONE, null);

    route.joinGroup(groupId, UPDATED_AT);

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    assertThat(route.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void givenSameGroup_whenJoinedAgain_thenUnchanged() {
    UUID groupId = UUID.randomUUID();
    WorkItemRoute route = route(UUID.randomUUID(), WorkItemRouteKind.LINKED_GROUP, groupId);

    route.joinGroup(groupId, UPDATED_AT);

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    assertThat(route.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void givenOtherGroup_whenJoined_thenRejected() {
    UUID workItemId = UUID.randomUUID();
    UUID existingGroupId = UUID.randomUUID();
    UUID newGroupId = UUID.randomUUID();
    WorkItemRoute route = route(workItemId, WorkItemRouteKind.LINKED_GROUP, existingGroupId);

    assertThatThrownBy(() -> route.joinGroup(newGroupId, UPDATED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Work item "
                + workItemId
                + " already routes to linked group "
                + existingGroupId
                + ", not "
                + newGroupId);
  }

  @Test
  void givenGroupRoute_whenLeft_thenStandalone() {
    UUID groupId = UUID.randomUUID();
    WorkItemRoute route = route(UUID.randomUUID(), WorkItemRouteKind.LINKED_GROUP, groupId);

    route.leaveGroup(groupId, UPDATED_AT);

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void givenStandaloneRoute_whenLeft_thenUnchanged() {
    WorkItemRoute route = route(UUID.randomUUID(), WorkItemRouteKind.STANDALONE, null);

    route.leaveGroup(UUID.randomUUID(), UPDATED_AT);

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void givenOtherGroup_whenLeft_thenRejected() {
    UUID workItemId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkItemRoute route = route(workItemId, WorkItemRouteKind.LINKED_GROUP, groupId);
    UUID expectedGroupId = UUID.randomUUID();

    assertThatThrownBy(() -> route.leaveGroup(expectedGroupId, UPDATED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Work item "
                + workItemId
                + " routes to linked group "
                + groupId
                + ", not "
                + expectedGroupId);
  }

  private WorkItemRoute route(UUID workItemId, WorkItemRouteKind kind, UUID groupId) {
    return new WorkItemRoute(WorkItemType.APPLICATION, workItemId, kind, groupId, 0L, CREATED_AT);
  }
}
