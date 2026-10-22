package uk.gov.justice.laa.dstew.access.command.worklist;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpectedAssignmentTest {
  private static final UUID ITEM_ID = UUID.randomUUID();

  @Test
  void givenNegativeVersion_whenCreated_thenRejected() {
    assertThatThrownBy(() -> new ExpectedAssignment(ITEM_ID, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version must not be negative");
  }

  @Test
  void givenSameOwner_whenOwnerRequired_thenAccepted() {
    assertThatCode(() -> new ExpectedAssignment(ITEM_ID, 0).requireOwner(ITEM_ID, ITEM_ID))
        .doesNotThrowAnyException();
  }

  @Test
  void givenOtherOwner_whenOwnerRequired_thenStaleConflict() {
    UUID otherOwnerId = UUID.randomUUID();

    assertThatThrownBy(() -> new ExpectedAssignment(otherOwnerId, 0).requireOwner(ITEM_ID, ITEM_ID))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + ITEM_ID + " cannot be updated: the assignment version is stale");
  }
}
