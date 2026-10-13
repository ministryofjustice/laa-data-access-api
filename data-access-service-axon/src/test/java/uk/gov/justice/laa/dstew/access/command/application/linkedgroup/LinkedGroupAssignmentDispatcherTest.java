package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;

/** Unit tests for {@link LinkedGroupAssignmentDispatcher}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("LinkedGroupAssignmentDispatcher")
class LinkedGroupAssignmentDispatcherTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-13T15:00:00Z");

  @Mock private LinkedGroupAssignmentRulesService rulesService;
  @Mock private CommandGateway commandGateway;

  @InjectMocks private LinkedGroupAssignmentDispatcher dispatcher;

  @Captor private ArgumentCaptor<DirectGroupWorkItemAssignmentCommand> commandCaptor;

  private UUID leadApplicationId;
  private UUID memberApplicationId;
  private UUID caseworkerId;

  @BeforeEach
  void setUp() {
    leadApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    memberApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    caseworkerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
  }

  @Test
  @DisplayName("AC1: Assigns member when decision is ASSIGN_MEMBER_TO_LEAD")
  void givenAssignMemberDecision_whenApplyAssignmentRules_thenDispatchesAssignmentCommand() {
    // Setup: RulesService returns ASSIGN_MEMBER_TO_LEAD decision
    LinkedGroupAssignmentDecision decision =
        LinkedGroupAssignmentDecision.assignMemberToLead(memberApplicationId, caseworkerId);
    when(rulesService.applyRules(leadApplicationId, memberApplicationId)).thenReturn(decision);

    // Execute
    dispatcher.applyAssignmentRules(leadApplicationId, memberApplicationId, OCCURRED_AT);

    // Assert: Command dispatched with correct target and caseworker
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.workItemId()).isEqualTo(memberApplicationId);
    Assertions.assertThat(command.caseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  @DisplayName("AC3: Assigns lead when decision is ASSIGN_LEAD_TO_MEMBER")
  void givenAssignLeadDecision_whenApplyAssignmentRules_thenDispatchesAssignmentCommand() {
    // Setup: RulesService returns ASSIGN_LEAD_TO_MEMBER decision
    LinkedGroupAssignmentDecision decision =
        LinkedGroupAssignmentDecision.assignLeadToMember(leadApplicationId, caseworkerId);
    when(rulesService.applyRules(leadApplicationId, memberApplicationId)).thenReturn(decision);

    // Execute
    dispatcher.applyAssignmentRules(leadApplicationId, memberApplicationId, OCCURRED_AT);

    // Assert: Command dispatched with correct target (lead) and caseworker
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.workItemId()).isEqualTo(leadApplicationId);
    Assertions.assertThat(command.caseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  @DisplayName("AC4: Does not dispatch command when decision is NONE")
  void givenNoChangeDecision_whenApplyAssignmentRules_thenDoesNotDispatchCommand() {
    // Setup: RulesService returns NONE decision
    LinkedGroupAssignmentDecision decision = LinkedGroupAssignmentDecision.none();
    when(rulesService.applyRules(leadApplicationId, memberApplicationId)).thenReturn(decision);

    // Execute
    dispatcher.applyAssignmentRules(leadApplicationId, memberApplicationId, OCCURRED_AT);

    // Assert: No command dispatched
    verifyNoInteractions(commandGateway);
  }

  @Test
  @DisplayName("Command includes proper metadata")
  void whenDispatchingCommand_thenIncludesProperMetadata() {
    // Setup
    LinkedGroupAssignmentDecision decision =
        LinkedGroupAssignmentDecision.assignMemberToLead(memberApplicationId, caseworkerId);
    when(rulesService.applyRules(leadApplicationId, memberApplicationId)).thenReturn(decision);

    // Execute
    dispatcher.applyAssignmentRules(leadApplicationId, memberApplicationId, OCCURRED_AT);

    // Assert: Command has proper metadata
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.eventDescription())
        .isEqualTo("Assignments for application and linked groups synchronized");
    Assertions.assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
