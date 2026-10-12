package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for WorkItemAssignment value object. Verifies the behavior of assignment state
 * representation and comparison logic used by the linked group assignment rules.
 */
class WorkItemAssignmentTest {

  private static final UUID CASEWORKER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CASEWORKER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

  // ============================================================================
  // isAssigned() Method Tests
  // ============================================================================

  @Test
  void givenAssignedToCaseworker_whenIsAssigned_thenReturnsTrue() {
    var assignment = new WorkItemAssignment(CASEWORKER_A, 1L);

    assertThat(assignment.isAssigned()).isTrue();
  }

  @Test
  void givenUnassigned_whenIsAssigned_thenReturnsFalse() {
    var assignment = new WorkItemAssignment(null, 0L);

    assertThat(assignment.isAssigned()).isFalse();
  }

  // ============================================================================
  // isSameCaseworkerAs() - Basic Cases (Both Assigned)
  // ============================================================================

  @Test
  void givenBothAssignedToSameCaseworker_whenCompare_thenReturnsTrue() {
    var assignment1 = new WorkItemAssignment(CASEWORKER_A, 1L);
    var assignment2 = new WorkItemAssignment(CASEWORKER_A, 2L);

    assertThat(assignment1.isSameCaseworkerAs(assignment2)).isTrue();
  }

  @Test
  void givenBothAssignedToDifferentCaseworkers_whenCompare_thenReturnsFalse() {
    var assignment1 = new WorkItemAssignment(CASEWORKER_A, 1L);
    var assignment2 = new WorkItemAssignment(CASEWORKER_B, 1L);

    assertThat(assignment1.isSameCaseworkerAs(assignment2)).isFalse();
  }

  // ============================================================================
  // isSameCaseworkerAs() - Mixed Cases (One Assigned, One Unassigned)
  // ============================================================================

  @Test
  void givenThisAssignedOtherUnassigned_whenCompare_thenReturnsFalse() {
    var assigned = new WorkItemAssignment(CASEWORKER_A, 1L);
    var unassigned = new WorkItemAssignment(null, 0L);

    assertThat(assigned.isSameCaseworkerAs(unassigned)).isFalse();
  }

  @Test
  void givenThisUnassignedOtherAssigned_whenCompare_thenReturnsFalse() {
    var unassigned = new WorkItemAssignment(null, 0L);
    var assigned = new WorkItemAssignment(CASEWORKER_A, 1L);

    assertThat(unassigned.isSameCaseworkerAs(assigned)).isFalse();
  }

  // ============================================================================
  // isSameCaseworkerAs() - Both Unassigned Cases
  // ============================================================================

  @Test
  void givenBothUnassigned_whenCompare_thenReturnsFalse() {
    // Note: When both are unassigned (null), isSameCaseworkerAs returns false
    // because the logic is: caseworkerId != null && caseworkerId.equals(other.caseworkerId())
    // Only the null parameter case returns true for unassigned (Scenario 4)
    var assignment1 = new WorkItemAssignment(null, 0L);
    var assignment2 = new WorkItemAssignment(null, 0L);

    assertThat(assignment1.isSameCaseworkerAs(assignment2)).isFalse();
  }

  // ============================================================================
  // isSameCaseworkerAs() - Null Parameter Cases
  // ============================================================================

  @Test
  void givenThisUnassignedOtherParameterNull_whenCompare_thenReturnsTrue() {
    var unassigned = new WorkItemAssignment(null, 0L);

    assertThat(unassigned.isSameCaseworkerAs(null)).isTrue();
  }

  @Test
  void givenThisAssignedOtherParameterNull_whenCompare_thenReturnsFalse() {
    var assigned = new WorkItemAssignment(CASEWORKER_A, 1L);

    assertThat(assigned.isSameCaseworkerAs(null)).isFalse();
  }

  // ============================================================================
  // isSameCaseworkerAs() - Version Independence
  // ============================================================================

  @Test
  void givenSameCaseworkerDifferentVersions_whenCompare_thenReturnsTrue() {
    var version1 = new WorkItemAssignment(CASEWORKER_A, 1L);
    var version5 = new WorkItemAssignment(CASEWORKER_A, 5L);
    var version10 = new WorkItemAssignment(CASEWORKER_A, 10L);

    assertThat(version1.isSameCaseworkerAs(version5)).isTrue();
    assertThat(version5.isSameCaseworkerAs(version10)).isTrue();
    assertThat(version1.isSameCaseworkerAs(version10)).isTrue();
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Test
  void givenMultipleComparisons_eachComparisonIsIndependent() {
    var appAssignment = new WorkItemAssignment(CASEWORKER_A, 1L);
    var groupAssignmentA = new WorkItemAssignment(CASEWORKER_A, 2L);
    var groupAssignmentB = new WorkItemAssignment(CASEWORKER_B, 1L);

    // App vs Group A should be true (same caseworker)
    assertThat(appAssignment.isSameCaseworkerAs(groupAssignmentA)).isTrue();

    // App vs Group B should be false (different caseworker)
    assertThat(appAssignment.isSameCaseworkerAs(groupAssignmentB)).isFalse();

    // Original app assignment should be unchanged
    assertThat(appAssignment.isAssigned()).isTrue();
  }
}
