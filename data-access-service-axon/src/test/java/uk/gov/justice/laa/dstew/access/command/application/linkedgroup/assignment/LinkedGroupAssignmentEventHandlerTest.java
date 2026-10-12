package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;

/**
 * Tests that LinkedGroupAssignmentEventHandler correctly listens for MemberAddedToGroupEvent and
 * delegates to LinkedGroupAssignmentCoordinator to apply assignment rules.
 */
@ExtendWith(MockitoExtension.class)
class LinkedGroupAssignmentEventHandlerTest {

  @Mock private LinkedGroupAssignmentCoordinator coordinator;

  @InjectMocks private LinkedGroupAssignmentEventHandler handler;

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID GROUP_LEAD_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Test
  void whenMemberAddedToGroupEvent_thenDelegateToCoordinator() {
    var event = new MemberAddedToGroupEvent(GROUP_ID, GROUP_LEAD_ID, APPLICATION_ID, OCCURRED_AT);

    handler.on(event);

    verify(coordinator).applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
  }

  @Test
  void whenEventWithDifferentMemberId_thenPassesCorrectApplicationId() {
    UUID differentApplicationId = UUID.randomUUID();
    var event =
        new MemberAddedToGroupEvent(GROUP_ID, GROUP_LEAD_ID, differentApplicationId, OCCURRED_AT);

    handler.on(event);

    verify(coordinator)
        .applyAssignmentRulesWhenLinking(differentApplicationId, GROUP_ID, OCCURRED_AT);
  }

  @Test
  void whenEventWithDifferentGroupId_thenPassesCorrectGroupId() {
    UUID differentGroupId = UUID.randomUUID();
    var event =
        new MemberAddedToGroupEvent(differentGroupId, GROUP_LEAD_ID, APPLICATION_ID, OCCURRED_AT);

    handler.on(event);

    verify(coordinator)
        .applyAssignmentRulesWhenLinking(APPLICATION_ID, differentGroupId, OCCURRED_AT);
  }

  @Test
  void whenEventWithDifferentOccurredAt_thenPassesCorrectTimestamp() {
    Instant differentOccurredAt = Instant.parse("2026-09-15T14:30:00Z");
    var event =
        new MemberAddedToGroupEvent(GROUP_ID, GROUP_LEAD_ID, APPLICATION_ID, differentOccurredAt);

    handler.on(event);

    verify(coordinator)
        .applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, differentOccurredAt);
  }

  @Test
  void whenEventHandled_thenCoordinatorInvokedExactlyOnce() {
    var event = new MemberAddedToGroupEvent(GROUP_ID, GROUP_LEAD_ID, APPLICATION_ID, OCCURRED_AT);

    handler.on(event);

    verify(coordinator).applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
  }

  @Test
  void whenMultipleEvents_thenEachDelegatedSeparately() {
    var event1 = new MemberAddedToGroupEvent(GROUP_ID, GROUP_LEAD_ID, APPLICATION_ID, OCCURRED_AT);
    UUID applicationId2 = UUID.randomUUID();
    var event2 =
        new MemberAddedToGroupEvent(
            GROUP_ID, GROUP_LEAD_ID, applicationId2, OCCURRED_AT.plusSeconds(60));

    handler.on(event1);
    handler.on(event2);

    verify(coordinator).applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
    verify(coordinator)
        .applyAssignmentRulesWhenLinking(applicationId2, GROUP_ID, OCCURRED_AT.plusSeconds(60));
  }

  @Test
  void eventDataExtraction_verifyAllParametersCorrect() {
    UUID expectedAppId = UUID.fromString("00000000-0000-0000-0000-000000000123");
    UUID expectedGroupId = UUID.fromString("00000000-0000-0000-0000-000000000456");
    UUID expectedLeadId = UUID.fromString("00000000-0000-0000-0000-000000000789");
    Instant expectedOccurredAt = Instant.parse("2026-10-12T15:00:00Z");

    var event =
        new MemberAddedToGroupEvent(
            expectedGroupId, expectedLeadId, expectedAppId, expectedOccurredAt);

    handler.on(event);

    // Verify all parameters are passed correctly
    verify(coordinator)
        .applyAssignmentRulesWhenLinking(expectedAppId, expectedGroupId, expectedOccurredAt);
  }
}
