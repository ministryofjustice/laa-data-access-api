package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link LinkedGroupAssignmentEventHandler}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("LinkedGroupAssignmentEventHandler")
class LinkedGroupAssignmentEventHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-13T15:00:00Z");

  @Mock private LinkedGroupAssignmentDispatcher dispatcher;

  @InjectMocks private LinkedGroupAssignmentEventHandler eventHandler;

  @Captor private ArgumentCaptor<UUID> leadApplicationIdCaptor;
  @Captor private ArgumentCaptor<UUID> memberApplicationIdCaptor;
  @Captor private ArgumentCaptor<Instant> occurredAtCaptor;

  private UUID groupId;
  private UUID leadApplicationId;
  private UUID memberApplicationId1;
  private UUID memberApplicationId2;

  @BeforeEach
  void setUp() {
    groupId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    leadApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    memberApplicationId1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    memberApplicationId2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
  }

  // ===== LinkedApplicationGroupCreatedEvent tests =====

  @Test
  @DisplayName("LinkedApplicationGroupCreatedEvent with 2+ members triggers assignment rules")
  void givenGroupCreatedWithTwoMembers_whenEventFired_thenTriggersAssignmentRules() {
    // Setup: Group created with lead + 1 member
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            groupId,
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId1),
            OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Dispatcher called with leadApplicationId (not groupId), member, and timestamp
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId1, OCCURRED_AT);
  }

  @Test
  @DisplayName("LinkedApplicationGroupCreatedEvent with 3+ members triggers for first member")
  void givenGroupCreatedWithThreeMembers_whenEventFired_thenTriggersWithFirstMember() {
    // Setup: Group created with lead + 2 members
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            groupId,
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId1, memberApplicationId2),
            OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Dispatcher called with first member (index 1)
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId1, OCCURRED_AT);
  }

  @Test
  @DisplayName("LinkedApplicationGroupCreatedEvent with only lead (1 member) does not trigger")
  void givenGroupCreatedWithOnlyLead_whenEventFired_thenDoesNotTrigger() {
    // Setup: Group created with only lead (size 1 < 2)
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadApplicationId, List.of(leadApplicationId), OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Dispatcher not called (requires at least 2 members)
    verifyNoInteractions(dispatcher);
  }

  @Test
  @DisplayName("LinkedApplicationGroupCreatedEvent with empty member list does not trigger")
  void givenGroupCreatedWithNoMembers_whenEventFired_thenDoesNotTrigger() {
    // Setup: Group created with empty member list (edge case)
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(groupId, leadApplicationId, List.of(), OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Dispatcher not called
    verifyNoInteractions(dispatcher);
  }

  // ===== MemberAddedToGroupEvent tests =====

  @Test
  @DisplayName("MemberAddedToGroupEvent triggers assignment rules")
  void givenMemberAddedToGroup_whenEventFired_thenTriggersAssignmentRules() {
    // Setup: Member added to existing group
    MemberAddedToGroupEvent event =
        new MemberAddedToGroupEvent(groupId, leadApplicationId, memberApplicationId1, OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Dispatcher called with leadApplicationId (from event), memberId, and timestamp
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId1, OCCURRED_AT);
  }

  @Test
  @DisplayName("MemberAddedToGroupEvent preserves correct lead and member IDs")
  void givenMemberAddedToGroup_whenEventFired_thenUsesCorrectIDs() {
    // Setup: Different member added
    MemberAddedToGroupEvent event =
        new MemberAddedToGroupEvent(groupId, leadApplicationId, memberApplicationId2, OCCURRED_AT);

    // Execute
    eventHandler.on(event);

    // Assert: Correct IDs passed (lead != member)
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId2, OCCURRED_AT);
  }

  @Test
  @DisplayName("MemberAddedToGroupEvent preserves timestamp")
  void givenMemberAddedWithTimestamp_whenEventFired_thenPreservesTimestamp() {
    // Setup
    Instant customTimestamp = Instant.parse("2026-10-13T12:34:56Z");
    MemberAddedToGroupEvent event =
        new MemberAddedToGroupEvent(
            groupId, leadApplicationId, memberApplicationId1, customTimestamp);

    // Execute
    eventHandler.on(event);

    // Assert: Exact timestamp propagated
    verify(dispatcher)
        .applyAssignmentRules(leadApplicationId, memberApplicationId1, customTimestamp);
  }

  // ===== Integration tests: Event flow =====

  @Test
  @DisplayName("Multiple member additions trigger separate assignment rules for each")
  void givenMultipleMembers_whenAddedSequentially_thenTriggersForEach() {
    // Setup: Add first member
    MemberAddedToGroupEvent event1 =
        new MemberAddedToGroupEvent(groupId, leadApplicationId, memberApplicationId1, OCCURRED_AT);

    // Execute: Handle first member addition
    eventHandler.on(event1);

    // Assert: First call made
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId1, OCCURRED_AT);

    // Setup: Add second member
    MemberAddedToGroupEvent event2 =
        new MemberAddedToGroupEvent(groupId, leadApplicationId, memberApplicationId2, OCCURRED_AT);

    // Execute: Handle second member addition
    eventHandler.on(event2);

    // Assert: Second call made (separate transaction)
    verify(dispatcher).applyAssignmentRules(leadApplicationId, memberApplicationId2, OCCURRED_AT);
  }
}
