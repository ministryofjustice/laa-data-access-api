package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationGroupRouteTest {
  private static final Instant CREATED_AT = Instant.parse("2026-09-14T09:00:00Z");
  private static final Instant LEFT_AT = Instant.parse("2026-09-14T10:00:00Z");

  @Test
  void givenLinkedRoute_whenLeave_thenClearsGroupAndUpdatesTimestamp() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var route =
        new ApplicationGroupRoute(
            applicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId, "1A001B", CREATED_AT);

    route.leave(groupId, LEFT_AT);

    assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getUpdatedAt()).isEqualTo(LEFT_AT);
  }

  @Test
  void givenStandaloneRoute_whenLeave_thenLeavesTimestampUnchanged() {
    var route =
        new ApplicationGroupRoute(
            UUID.randomUUID(), ApplicationGroupRouteKind.STANDALONE, null, "1A001B", CREATED_AT);

    route.leave(UUID.randomUUID(), LEFT_AT);

    assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void givenDifferentGroup_whenLeave_thenThrowsIllegalState() {
    UUID applicationId = UUID.randomUUID();
    UUID existingGroupId = UUID.randomUUID();
    UUID requestedGroupId = UUID.randomUUID();
    var route =
        new ApplicationGroupRoute(
            applicationId,
            ApplicationGroupRouteKind.LINKED_GROUP,
            existingGroupId,
            "1A001B",
            CREATED_AT);

    assertThatThrownBy(() -> route.leave(requestedGroupId, LEFT_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Application "
                + applicationId
                + " belongs to linked group "
                + existingGroupId
                + ", not "
                + requestedGroupId);
  }
}
