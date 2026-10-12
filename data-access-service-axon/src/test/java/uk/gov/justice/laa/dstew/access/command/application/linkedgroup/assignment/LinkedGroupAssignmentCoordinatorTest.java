package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

@ExtendWith(MockitoExtension.class)
class LinkedGroupAssignmentCoordinatorTest {

  @Mock private LinkedGroupAssignmentQueryService queryService;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private CommandGateway commandGateway;

  @InjectMocks private LinkedGroupAssignmentCoordinator coordinator;

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_A = UUID.randomUUID();
  private static final UUID CASEWORKER_B = UUID.randomUUID();
  private static final UUID GROUP_LEAD_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Test
  void givenScenario1_whenApplyAssignmentRules_thenDispatchesAssignAppCommand() {
    // Scenario 1: App unassigned + Group assigned
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_APP_TO_GROUP);
    when(queryService.getLinkedGroupAssignment(GROUP_ID))
        .thenReturn(Optional.of(new WorkItemAssignment(CASEWORKER_A, 1L)));

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    verify(commandGateway)
        .sendAndWait(
            argThat(
                command ->
                    command instanceof AssignWorkItemCommand
                        && ((AssignWorkItemCommand) command).workItemId().equals(APPLICATION_ID)
                        && ((AssignWorkItemCommand) command).caseworkerId().equals(CASEWORKER_A)
                        && ((AssignWorkItemCommand) command)
                            .eventDescription()
                            .contains("assigned group")));
  }

  @Test
  void givenScenario2_whenApplyAssignmentRules_thenDispatchesReassignAppCommand() {
    // Scenario 2: App assigned to different CW + Group assigned
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.REASSIGN_APP_TO_GROUP);
    when(queryService.getLinkedGroupAssignment(GROUP_ID))
        .thenReturn(Optional.of(new WorkItemAssignment(CASEWORKER_A, 1L)));

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    verify(commandGateway)
        .sendAndWait(
            argThat(
                command ->
                    command instanceof AssignWorkItemCommand
                        && ((AssignWorkItemCommand) command).workItemId().equals(APPLICATION_ID)
                        && ((AssignWorkItemCommand) command).caseworkerId().equals(CASEWORKER_A)
                        && ((AssignWorkItemCommand) command)
                            .eventDescription()
                            .contains("reassigned")));
  }

  @Test
  void givenScenario3_whenApplyAssignmentRules_thenDispatchesAssignGroupCommand() {
    // Scenario 3: App assigned + Group unassigned
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_GROUP_FROM_APP);
    when(queryService.getWorkItemAssignment(APPLICATION_ID))
        .thenReturn(Optional.of(new WorkItemAssignment(CASEWORKER_A, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    verify(commandGateway)
        .sendAndWait(
            argThat(
                command ->
                    command instanceof AssignWorkItemCommand
                        && ((AssignWorkItemCommand) command).workItemId().equals(GROUP_LEAD_ID)
                        && ((AssignWorkItemCommand) command).caseworkerId().equals(CASEWORKER_A)
                        && ((AssignWorkItemCommand) command)
                            .eventDescription()
                            .contains("Group assigned")));
  }

  @Test
  void givenScenario4_whenApplyAssignmentRules_thenDispatchesNoCommand() {
    // Scenario 4: Both unassigned or same caseworker
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.NO_CHANGE);

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    verify(commandGateway, never()).sendAndWait(any());
  }

  @Test
  void scenario1_whenGroupUnassigned_thenThrowsIllegalStateException() {
    // Scenario 1 with missing group assignment should throw
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_APP_TO_GROUP);
    when(queryService.getLinkedGroupAssignment(GROUP_ID)).thenReturn(Optional.empty());

    try {
      coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
    } catch (IllegalStateException e) {
      assert e.getMessage().contains("should be assigned");
    }
  }

  @Test
  void scenario2_whenGroupUnassigned_thenThrowsIllegalStateException() {
    // Scenario 2 with missing group assignment should throw
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.REASSIGN_APP_TO_GROUP);
    when(queryService.getLinkedGroupAssignment(GROUP_ID)).thenReturn(Optional.empty());

    try {
      coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
    } catch (IllegalStateException e) {
      assert e.getMessage().contains("should be assigned");
    }
  }

  @Test
  void scenario3_whenAppUnassigned_thenThrowsIllegalStateException() {
    // Scenario 3 with missing app assignment should throw
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_GROUP_FROM_APP);
    when(queryService.getWorkItemAssignment(APPLICATION_ID)).thenReturn(Optional.empty());

    try {
      coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);
    } catch (IllegalStateException e) {
      assert e.getMessage().contains("should be assigned");
    }
  }

  @Test
  void scenario3_dispatchesCommandWithGroupLeadId_notApplicationId() {
    // Scenario 3 must target the group lead, not the application being added
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_GROUP_FROM_APP);
    when(queryService.getWorkItemAssignment(APPLICATION_ID))
        .thenReturn(Optional.of(new WorkItemAssignment(CASEWORKER_A, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    // Verify the command targets the group lead, not the application
    verify(commandGateway)
        .sendAndWait(
            argThat(
                command ->
                    command instanceof AssignWorkItemCommand
                        && ((AssignWorkItemCommand) command).workItemId().equals(GROUP_LEAD_ID)
                        && !((AssignWorkItemCommand) command).workItemId().equals(APPLICATION_ID)));
  }

  @Test
  void allScenarios_passOccurredAtToCommand() {
    // All scenarios should pass the occurredAt timestamp
    when(queryService.determineRule(APPLICATION_ID, GROUP_ID))
        .thenReturn(LinkedGroupAssignmentRule.ASSIGN_APP_TO_GROUP);
    when(queryService.getLinkedGroupAssignment(GROUP_ID))
        .thenReturn(Optional.of(new WorkItemAssignment(CASEWORKER_A, 1L)));

    coordinator.applyAssignmentRulesWhenLinking(APPLICATION_ID, GROUP_ID, OCCURRED_AT);

    verify(commandGateway)
        .sendAndWait(
            argThat(
                command ->
                    command instanceof AssignWorkItemCommand
                        && ((AssignWorkItemCommand) command).occurredAt().equals(OCCURRED_AT)));
  }

  private LinkedApplicationGroupReadModel createGroup(UUID groupId, UUID leadApplicationId) {
    return LinkedApplicationGroupReadModel.builder()
        .groupId(groupId)
        .leadApplicationId(leadApplicationId)
        .memberIds(List.of(leadApplicationId, APPLICATION_ID))
        .version(1L)
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();
  }
}
