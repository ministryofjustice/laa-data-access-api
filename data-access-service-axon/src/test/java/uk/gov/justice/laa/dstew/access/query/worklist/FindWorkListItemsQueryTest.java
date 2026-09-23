package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for work-list query input normalisation and validation. */
class FindWorkListItemsQueryTest {

  @Test
  void defaultsToOpenApplicationsAndPaginationWhenFiltersAreNotProvided() {
    FindWorkListItemsQuery query = new FindWorkListItemsQuery(null, null, null, null, null, null);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
    assertThat(query.assignedToMe()).isFalse();
    assertThat(query.unassigned()).isTrue();
  }

  @Test
  void preservesARequestForTheAuthenticatedCaseworkersPersonalQueue() {
    UUID authenticatedUserId = UUID.randomUUID();
    FindWorkListItemsQuery query =
        new FindWorkListItemsQuery(true, null, false, null, null, authenticatedUserId);

    assertThat(query.assignedToMe()).isTrue();
    assertThat(query.unassigned()).isFalse();
    assertThat(query.authenticatedUserId()).isEqualTo(authenticatedUserId);
  }

  @Test
  void acceptsTheCombinedOpenApplicationsAndPersonalQueueFilter() {
    FindWorkListItemsQuery query =
        new FindWorkListItemsQuery(true, null, true, null, null, UUID.randomUUID());

    assertThat(query.assignedToMe()).isTrue();
    assertThat(query.unassigned()).isTrue();
  }

  @Test
  void rejectsARequestThatWouldReturnAllWorkQueueItems() {
    assertThatThrownBy(() -> new FindWorkListItemsQuery(false, null, false, 1, 20, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("assignedToMe and unassigned cannot both be false");
  }

  @Test
  void rejectsAPersonalQueueRequestWithoutAnAuthenticatedUserId() {
    assertThatThrownBy(() -> new FindWorkListItemsQuery(true, null, false, 1, 20, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authenticatedUserId is required when assignedToMe is true");
  }
}
