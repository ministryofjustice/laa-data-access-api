package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for work-list query input normalisation and validation. */
class FindWorkListItemsQueryTest {

  @Test
  void defaultsPaginationWhenItIsNotProvided() {
    FindWorkListItemsQuery query = new FindWorkListItemsQuery(null, null, null, null, null);

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
    assertThat(query.unassigned()).isTrue();
  }

  @Test
  void acceptsAssignedToWithoutAnUnassignedFilter() {
    UUID caseworkerId = UUID.randomUUID();

    FindWorkListItemsQuery query = new FindWorkListItemsQuery(caseworkerId, null, null, null, null);

    assertThat(query.assignedTo()).isEqualTo(caseworkerId);
    assertThat(query.unassigned()).isNull();
  }

  @Test
  void preservesAnExplicitFalseUnassignedFilter() {
    FindWorkListItemsQuery query = new FindWorkListItemsQuery(null, null, false, null, null);

    assertThat(query.unassigned()).isFalse();
    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
  }

  @Test
  void rejectsConflictingUnassignedAndAssignedToFilters() {
    assertThatThrownBy(() -> new FindWorkListItemsQuery(UUID.randomUUID(), null, true, 1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("assignedTo and unassigned=true cannot be used together");
  }
}
