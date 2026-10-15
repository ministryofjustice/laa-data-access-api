package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
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
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Unit tests for {@link LinkedGroupAssignmentDispatcher}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("LinkedGroupAssignmentDispatcher")
class LinkedGroupAssignmentDispatcherTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-13T15:00:00Z");

  @Mock private ApplicationReadRepository applicationReadRepository;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private CommandGateway commandGateway;

  @InjectMocks private LinkedGroupAssignmentDispatcher dispatcher;

  @Captor private ArgumentCaptor<DirectGroupWorkItemAssignmentCommand> commandCaptor;

  private UUID groupId;
  private UUID leadApplicationId;
  private UUID memberApplicationId;
  private UUID caseworkerId1;
  private UUID caseworkerId2;

  @BeforeEach
  void setUp() {
    groupId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    leadApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    memberApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    caseworkerId1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    caseworkerId2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
  }

  // ===== AC1: Lead assigned, member unassigned =====

  @Test
  @DisplayName("AC1: Lead assigned, member unassigned → dispatches command to assign member")
  void
      givenLeadAssignedAndMemberUnassigned_whenApplyAssignmentRules_thenDispatchesAssignmentCommand() {
    // Setup: Lead has caseworker, member does not
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: Command dispatched with member as target
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.workItemId()).isEqualTo(memberApplicationId);
    Assertions.assertThat(command.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC2: Both assigned to different caseworkers =====

  @Test
  @DisplayName(
      "AC2: Both assigned to different caseworkers → dispatches command to reassign member")
  void
      givenBothAssignedToDifferentCaseworkers_whenApplyAssignmentRules_thenDispatchesAssignmentCommand() {
    // Setup: Both assigned, but to different caseworkers
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, caseworkerId2);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: Command dispatched with member as target
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.workItemId()).isEqualTo(memberApplicationId);
    Assertions.assertThat(command.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC3: Member assigned, lead unassigned =====

  @Test
  @DisplayName("AC3: Member assigned, lead unassigned → dispatches command to assign lead")
  void
      givenMemberAssignedAndLeadUnassigned_whenApplyAssignmentRules_thenDispatchesAssignmentCommand() {
    // Setup: Member has caseworker, lead does not
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, null);
    mockApplicationCaseworker(memberApplicationId, caseworkerId1);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: Command dispatched with lead as target
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.workItemId()).isEqualTo(leadApplicationId);
    Assertions.assertThat(command.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC4: Neither assigned or both assigned to same caseworker =====

  @Test
  @DisplayName("AC4: Neither assigned → does not dispatch command")
  void givenNeitherAssigned_whenApplyAssignmentRules_thenDoesNotDispatchCommand() {
    // Setup: Neither application has a caseworker
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, null);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: No command dispatched
    verifyNoInteractions(commandGateway);
  }

  @Test
  @DisplayName("AC4: Both assigned to same caseworker → does not dispatch command")
  void givenBothAssignedToSameCaseworker_whenApplyAssignmentRules_thenDoesNotDispatchCommand() {
    // Setup: Both assigned to the same caseworker
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, caseworkerId1);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: No command dispatched
    verifyNoInteractions(commandGateway);
  }

  @Test
  @DisplayName("Command includes proper metadata")
  void whenDispatchingCommand_thenIncludesProperMetadata() {
    // Setup
    mockGroup(groupId, leadApplicationId);
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: Command has proper metadata
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    DirectGroupWorkItemAssignmentCommand command = commandCaptor.getValue();
    Assertions.assertThat(command.eventDescription())
        .isEqualTo("Assignments for application and linked groups synchronized");
    Assertions.assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  @DisplayName("When group does not exist → does not dispatch command")
  void givenGroupDoesNotExist_whenApplyAssignmentRules_thenDoesNotDispatchCommand() {
    // Setup: Group not found
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.empty());

    // Execute
    dispatcher.applyAssignmentRules(groupId, memberApplicationId, OCCURRED_AT);

    // Assert: No command dispatched
    verifyNoInteractions(commandGateway);
  }

  private void mockGroup(UUID groupId, UUID leadApplicationId) {
    LinkedApplicationGroupReadModel group = new LinkedApplicationGroupReadModel();
    group.setLeadApplicationId(leadApplicationId);
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(group));
  }

  private void mockApplicationCaseworker(UUID applicationId, UUID caseworkerId) {
    if (caseworkerId == null) {
      when(applicationReadRepository.findById(applicationId))
          .thenReturn(
              Optional.of(
                  new ApplicationReadModel() {
                    @Override
                    public UUID getCaseworkerId() {
                      return null;
                    }
                  }));
    } else {
      ApplicationReadModel model =
          new ApplicationReadModel() {
            @Override
            public UUID getCaseworkerId() {
              return caseworkerId;
            }
          };
      when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(model));
    }
  }
}
