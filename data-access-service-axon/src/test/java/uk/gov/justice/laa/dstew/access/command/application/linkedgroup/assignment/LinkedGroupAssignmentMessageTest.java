package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupRemovalResult;

class LinkedGroupAssignmentMessageTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-22T09:00:00Z");

  @Test
  void givenNullCaseworker_whenAssignCommandCreated_thenRejected() {
    assertThatThrownBy(
            () ->
                new AssignLinkedGroupCommand(
                    UUID.randomUUID(), UUID.randomUUID(), null, 0, OCCURRED_AT))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("caseworkerId must not be null");
  }

  @Test
  void givenNullCaseworker_whenValidateCommandCreated_thenRejected() {
    assertThatThrownBy(
            () ->
                new ValidateLinkedGroupAssigneeCommand(UUID.randomUUID(), UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("caseworkerId must not be null");
  }

  @Test
  void givenMutableList_whenRemovalResultCreated_thenListIsCopied() {
    var leavingApplicationIds = new ArrayList<>(List.of(UUID.randomUUID()));
    var result = new LinkedGroupRemovalResult(UUID.randomUUID(), leavingApplicationIds, null, 0);

    leavingApplicationIds.add(UUID.randomUUID());

    assertThat(result.leavingApplicationIds()).hasSize(1);
    assertThatThrownBy(() -> result.leavingApplicationIds().add(UUID.randomUUID()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenIncompleteAssignments_whenEstablishCommandCreated_thenRejected() {
    var leadApplicationId = UUID.randomUUID();
    var memberApplicationId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                new EstablishLinkedApplicationGroupCommand(
                    UUID.randomUUID(),
                    leadApplicationId,
                    List.of(leadApplicationId, memberApplicationId),
                    Map.of(leadApplicationId, new DelegatedAssignment(null)),
                    OCCURRED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("joiningAssignments must contain exactly the member application IDs");
  }
}
