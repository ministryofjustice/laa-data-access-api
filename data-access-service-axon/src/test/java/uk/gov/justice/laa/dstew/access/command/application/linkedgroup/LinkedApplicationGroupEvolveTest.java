package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupEvolveTest {

  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID LEAD_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-22T09:00:00Z");

  @Test
  void givenCreatedEvent_whenEvolved_thenGroupStartsUnassigned() {
    var state = new LinkedApplicationGroupState();

    LinkedApplicationGroupEvolve.apply(
        state,
        new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), AT));

    assertThat(state.caseworkerId).isNull();
    assertThat(state.assignmentVersion).isZero();
  }

  @Test
  void givenMemberAddedEvent_whenEvolved_thenAssignmentUnchanged() {
    var state = assignedState();

    LinkedApplicationGroupEvolve.apply(
        state, new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, UUID.randomUUID(), AT));

    assertThat(state.caseworkerId).isEqualTo(CASEWORKER_ID);
    assertThat(state.assignmentVersion).isEqualTo(4);
  }

  @Test
  void givenAssignedEvent_whenEvolved_thenRecordsCaseworkerAndVersion() {
    var state = new LinkedApplicationGroupState();

    LinkedApplicationGroupEvolve.apply(
        state, new LinkedGroupAssignedEvent(GROUP_ID, CASEWORKER_ID, 2, AT));

    assertThat(state.caseworkerId).isEqualTo(CASEWORKER_ID);
    assertThat(state.assignmentVersion).isEqualTo(2);
  }

  @Test
  void givenUnassignedEvent_whenEvolved_thenClearsCaseworker() {
    var state = assignedState();

    LinkedApplicationGroupEvolve.apply(state, new LinkedGroupUnassignedEvent(GROUP_ID, 5, AT));

    assertThat(state.caseworkerId).isNull();
    assertThat(state.assignmentVersion).isEqualTo(5);
  }

  @Test
  void givenLeadChangedEvent_whenEvolved_thenAssignmentUnchanged() {
    var state = assignedState();

    LinkedApplicationGroupEvolve.apply(
        state, new LinkedApplicationGroupLeadChangedEvent(GROUP_ID, LEAD_ID, MEMBER_ID, 1, AT));

    assertThat(state.caseworkerId).isEqualTo(CASEWORKER_ID);
    assertThat(state.assignmentVersion).isEqualTo(4);
  }

  private static LinkedApplicationGroupState assignedState() {
    var state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(
        state,
        new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), AT));
    LinkedApplicationGroupEvolve.apply(
        state, new LinkedGroupAssignedEvent(GROUP_ID, CASEWORKER_ID, 4, AT));
    return state;
  }
}
