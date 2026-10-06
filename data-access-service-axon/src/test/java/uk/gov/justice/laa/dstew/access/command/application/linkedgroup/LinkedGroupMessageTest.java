package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedGroupMessageTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-28T11:00:00Z");

  @Test
  void givenValues_whenChangeLeadCommandCreated_thenValuesArePreserved() {
    var groupId = UUID.randomUUID();
    var newLeadId = UUID.randomUUID();
    var command = new ChangeLinkedGroupLeadCommand(groupId, newLeadId, 4L, OCCURRED_AT);

    assertThat(command.groupId()).isEqualTo(groupId);
    assertThat(command.newLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(command.expectedGroupVersion()).isEqualTo(4L);
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenValues_whenRemoveMemberCommandCreated_thenValuesArePreserved() {
    var groupId = UUID.randomUUID();
    var applicationId = UUID.randomUUID();
    var command =
        new RemoveApplicationFromLinkedGroupCommand(groupId, applicationId, 5L, OCCURRED_AT);

    assertThat(command.groupId()).isEqualTo(groupId);
    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedGroupVersion()).isEqualTo(5L);
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenValues_whenLeadChangeEventCreated_thenValuesArePreserved() {
    var groupId = UUID.randomUUID();
    var previousLeadId = UUID.randomUUID();
    var newLeadId = UUID.randomUUID();
    var event =
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, previousLeadId, newLeadId, 6L, OCCURRED_AT);

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.previousLeadApplicationId()).isEqualTo(previousLeadId);
    assertThat(event.newLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(event.groupVersion()).isEqualTo(6L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenValues_whenMemberRemovedEventCreated_thenValuesArePreserved() {
    var groupId = UUID.randomUUID();
    var leadId = UUID.randomUUID();
    var memberId = UUID.randomUUID();
    var event = new MemberRemovedFromGroupEvent(groupId, leadId, memberId, 7L, OCCURRED_AT);

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.leadApplicationId()).isEqualTo(leadId);
    assertThat(event.memberId()).isEqualTo(memberId);
    assertThat(event.groupVersion()).isEqualTo(7L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(event).isInstanceOf(LinkedGroupMemberRemoval.class);
  }

  @Test
  void givenValues_whenGroupDissolvedEventCreated_thenValuesArePreserved() {
    var groupId = UUID.randomUUID();
    var leadId = UUID.randomUUID();
    var removedId = UUID.randomUUID();
    var memberIds = List.of(leadId, removedId);
    var event =
        new LinkedApplicationGroupDissolvedEvent(
            groupId, leadId, removedId, memberIds, 8L, OCCURRED_AT);

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.formerLeadApplicationId()).isEqualTo(leadId);
    assertThat(event.removedApplicationId()).isEqualTo(removedId);
    assertThat(event.memberApplicationIds()).containsExactlyElementsOf(memberIds);
    assertThat(event.groupVersion()).isEqualTo(8L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(event).isInstanceOf(LinkedGroupMemberRemoval.class);
  }

  @Test
  void givenValues_whenMakeLeadCommandCreated_thenValuesArePreserved() {
    var applicationId = UUID.randomUUID();
    var expectedGroup = new ExpectedLinkedGroup(UUID.randomUUID(), 9L);
    var command = new MakeApplicationLeadCommand(applicationId, expectedGroup, OCCURRED_AT);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedGroup()).isEqualTo(expectedGroup);
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenNegativeVersion_whenExpectedGroupCreated_thenThrowsIllegalArgument() {
    assertThatThrownBy(() -> new ExpectedLinkedGroup(UUID.randomUUID(), -1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version must not be negative");
  }

  @Test
  void givenNullGroupId_whenExpectedGroupCreated_thenThrowsNullPointer() {
    assertThatThrownBy(() -> new ExpectedLinkedGroup(null, 0L))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("groupId must not be null");
  }

  @Test
  void givenNullExpectedGroup_whenMakeLeadCommandCreated_thenThrowsNullPointer() {
    assertThatThrownBy(() -> new MakeApplicationLeadCommand(UUID.randomUUID(), null, OCCURRED_AT))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("expectedGroup must not be null");
  }

  @Test
  void givenValues_whenUnlinkCommandCreated_thenValuesArePreserved() {
    var applicationId = UUID.randomUUID();
    var expectedGroup = new ExpectedLinkedGroup(UUID.randomUUID(), 10L);
    var command = new UnlinkApplicationCommand(applicationId, expectedGroup, OCCURRED_AT);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedGroup()).isEqualTo(expectedGroup);
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenNullExpectedGroup_whenUnlinkCommandCreated_thenThrowsNullPointer() {
    assertThatThrownBy(() -> new UnlinkApplicationCommand(UUID.randomUUID(), null, OCCURRED_AT))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("expectedGroup must not be null");
  }
}
