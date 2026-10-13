package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecision.Action;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

/** Unit tests for {@link LinkedGroupAssignmentRulesService} — tests all 4 AC scenarios. */
@ExtendWith(MockitoExtension.class)
@DisplayName("LinkedGroupAssignmentRulesService")
class LinkedGroupAssignmentRulesServiceTest {

  @Mock private ApplicationReadRepository applicationReadRepository;

  @InjectMocks private LinkedGroupAssignmentRulesService rulesService;

  private UUID leadApplicationId;
  private UUID memberApplicationId;
  private UUID caseworkerId1;
  private UUID caseworkerId2;

  @BeforeEach
  void setUp() {
    leadApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    memberApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    caseworkerId1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    caseworkerId2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
  }

  // ===== AC1: Lead assigned, member unassigned =====

  @Test
  @DisplayName("AC1: Lead assigned, member unassigned → assign member to lead's caseworker")
  void givenLeadAssignedAndMemberUnassigned_whenApplyRules_thenAssignMemberToLead() {
    // Setup: Lead has caseworker, member does not
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert
    assertThat(decision.action()).isEqualTo(Action.ASSIGN_MEMBER_TO_LEAD);
    assertThat(decision.targetApplicationId()).isEqualTo(memberApplicationId);
    assertThat(decision.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC2: Both assigned but to different caseworkers =====

  @Test
  @DisplayName("AC2: Both assigned to different caseworkers → reassign member to lead's caseworker")
  void givenBothAssignedToDifferentCaseworkers_whenApplyRules_thenReassignMemberToLead() {
    // Setup: Both assigned, but to different caseworkers
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, caseworkerId2);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert
    assertThat(decision.action()).isEqualTo(Action.ASSIGN_MEMBER_TO_LEAD);
    assertThat(decision.targetApplicationId()).isEqualTo(memberApplicationId);
    assertThat(decision.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC3: Member assigned, lead unassigned =====

  @Test
  @DisplayName("AC3: Member assigned, lead unassigned → assign lead to member's caseworker")
  void givenMemberAssignedAndLeadUnassigned_whenApplyRules_thenAssignLeadToMember() {
    // Setup: Member has caseworker, lead does not
    mockApplicationCaseworker(leadApplicationId, null);
    mockApplicationCaseworker(memberApplicationId, caseworkerId1);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert
    assertThat(decision.action()).isEqualTo(Action.ASSIGN_LEAD_TO_MEMBER);
    assertThat(decision.targetApplicationId()).isEqualTo(leadApplicationId);
    assertThat(decision.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== AC4: Neither assigned or both assigned to same caseworker =====

  @Test
  @DisplayName("AC4a: Neither assigned → no change")
  void givenNeitherAssigned_whenApplyRules_thenNoChange() {
    // Setup: Both unassigned
    mockApplicationCaseworker(leadApplicationId, null);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert
    assertThat(decision.action()).isEqualTo(Action.NONE);
    assertThat(decision.targetApplicationId()).isNull();
    assertThat(decision.caseworkerId()).isNull();
  }

  @Test
  @DisplayName("AC4b: Both assigned to same caseworker → no change")
  void givenBothAssignedToSameCaseworker_whenApplyRules_thenNoChange() {
    // Setup: Both assigned to same caseworker
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, caseworkerId1);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert
    assertThat(decision.action()).isEqualTo(Action.NONE);
    assertThat(decision.targetApplicationId()).isNull();
    assertThat(decision.caseworkerId()).isNull();
  }

  // ===== Edge case: Application doesn't exist =====

  @Test
  @DisplayName("Edge case: Lead application doesn't exist → treated as unassigned")
  void givenLeadApplicationDoesNotExist_whenApplyRules_thenTreatedAsUnassigned() {
    // Setup: Lead doesn't exist (no read-model row), member is assigned
    mockApplicationCaseworker(leadApplicationId, null);
    mockApplicationCaseworker(memberApplicationId, caseworkerId1);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert: Should treat as AC3 (member assigned, lead unassigned)
    assertThat(decision.action()).isEqualTo(Action.ASSIGN_LEAD_TO_MEMBER);
    assertThat(decision.targetApplicationId()).isEqualTo(leadApplicationId);
    assertThat(decision.caseworkerId()).isEqualTo(caseworkerId1);
  }

  @Test
  @DisplayName("Edge case: Member application doesn't exist → treated as unassigned")
  void givenMemberApplicationDoesNotExist_whenApplyRules_thenTreatedAsUnassigned() {
    // Setup: Member doesn't exist (no read-model row), lead is assigned
    mockApplicationCaseworker(leadApplicationId, caseworkerId1);
    mockApplicationCaseworker(memberApplicationId, null);

    // Execute
    LinkedGroupAssignmentDecision decision =
        rulesService.applyRules(leadApplicationId, memberApplicationId);

    // Assert: Should treat as AC1 (lead assigned, member unassigned)
    assertThat(decision.action()).isEqualTo(Action.ASSIGN_MEMBER_TO_LEAD);
    assertThat(decision.targetApplicationId()).isEqualTo(memberApplicationId);
    assertThat(decision.caseworkerId()).isEqualTo(caseworkerId1);
  }

  // ===== Helpers =====

  private void mockApplicationCaseworker(UUID applicationId, UUID caseworkerId) {
    if (caseworkerId != null) {
      ApplicationReadModel readModel = org.mockito.Mockito.mock(ApplicationReadModel.class);
      when(readModel.getCaseworkerId()).thenReturn(caseworkerId);
      when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(readModel));
    } else {
      when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    }
  }
}
