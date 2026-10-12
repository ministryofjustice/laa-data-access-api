package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadRepository;

@ExtendWith(MockitoExtension.class)
class LinkedGroupAssignmentQueryServiceTest {

  @Mock private ApplicationGroupRouteRepository groupRouteRepository;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private WorkListItemReadRepository workListItemRepository;

  @InjectMocks private LinkedGroupAssignmentQueryService service;

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_A = UUID.randomUUID();
  private static final UUID CASEWORKER_B = UUID.randomUUID();
  private static final UUID GROUP_LEAD_ID = UUID.randomUUID();

  @Test
  void givenAppUnassignedAndGroupAssigned_whenDetermineRule_thenReturnsAssignAppToGroup() {
    // Scenario 1: App unassigned + Group assigned
    when(workListItemRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, CASEWORKER_A, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var rule = service.determineRule(APPLICATION_ID, GROUP_ID);

    assertThat(rule).isEqualTo(LinkedGroupAssignmentRule.ASSIGN_APP_TO_GROUP);
  }

  @Test
  void
      givenAppAssignedToDifferentCWAndGroupAssigned_whenDetermineRule_thenReturnsReassignAppToGroup() {
    // Scenario 2: App assigned to different CW + Group assigned
    when(workListItemRepository.findById(APPLICATION_ID))
        .thenReturn(Optional.of(createWorkItem(APPLICATION_ID, CASEWORKER_B, 1L)));
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, CASEWORKER_A, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var rule = service.determineRule(APPLICATION_ID, GROUP_ID);

    assertThat(rule).isEqualTo(LinkedGroupAssignmentRule.REASSIGN_APP_TO_GROUP);
  }

  @Test
  void givenAppAssignedAndGroupUnassigned_whenDetermineRule_thenReturnsAssignGroupFromApp() {
    // Scenario 3: App assigned + Group unassigned
    when(workListItemRepository.findById(APPLICATION_ID))
        .thenReturn(Optional.of(createWorkItem(APPLICATION_ID, CASEWORKER_A, 1L)));
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, null, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var rule = service.determineRule(APPLICATION_ID, GROUP_ID);

    assertThat(rule).isEqualTo(LinkedGroupAssignmentRule.ASSIGN_GROUP_FROM_APP);
  }

  @Test
  void givenBothUnassigned_whenDetermineRule_thenReturnsNoChange() {
    // Scenario 4a: Both unassigned
    when(workListItemRepository.findById(APPLICATION_ID))
        .thenReturn(Optional.of(createWorkItem(APPLICATION_ID, null, 1L)));
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, null, 1L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var rule = service.determineRule(APPLICATION_ID, GROUP_ID);

    assertThat(rule).isEqualTo(LinkedGroupAssignmentRule.NO_CHANGE);
  }

  @Test
  void givenBothAssignedToSameCaseworker_whenDetermineRule_thenReturnsNoChange() {
    // Scenario 4b: Both assigned to same caseworker
    when(workListItemRepository.findById(APPLICATION_ID))
        .thenReturn(Optional.of(createWorkItem(APPLICATION_ID, CASEWORKER_A, 1L)));
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, CASEWORKER_A, 2L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var rule = service.determineRule(APPLICATION_ID, GROUP_ID);

    assertThat(rule).isEqualTo(LinkedGroupAssignmentRule.NO_CHANGE);
  }

  @Test
  void givenValidWorkItem_whenGetWorkItemAssignment_thenReturnsAssignment() {
    when(workListItemRepository.findById(APPLICATION_ID))
        .thenReturn(Optional.of(createWorkItem(APPLICATION_ID, CASEWORKER_A, 3L)));

    var assignment = service.getWorkItemAssignment(APPLICATION_ID);

    assertThat(assignment).isPresent();
    assertThat(assignment.get().caseworkerId()).isEqualTo(CASEWORKER_A);
    assertThat(assignment.get().assignmentVersion()).isEqualTo(3L);
  }

  @Test
  void givenMissingWorkItem_whenGetWorkItemAssignment_thenReturnsEmpty() {
    when(workListItemRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

    var assignment = service.getWorkItemAssignment(APPLICATION_ID);

    assertThat(assignment).isEmpty();
  }

  @Test
  void givenValidGroup_whenGetLinkedGroupAssignment_thenReturnsGroupAssignment() {
    when(workListItemRepository.findById(GROUP_LEAD_ID))
        .thenReturn(Optional.of(createWorkItem(GROUP_LEAD_ID, CASEWORKER_A, 5L)));
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var assignment = service.getLinkedGroupAssignment(GROUP_ID);

    assertThat(assignment).isPresent();
    assertThat(assignment.get().caseworkerId()).isEqualTo(CASEWORKER_A);
    assertThat(assignment.get().assignmentVersion()).isEqualTo(5L);
  }

  @Test
  void givenGroupNotFound_whenFindGroupLeadApplicationId_thenThrowsResourceNotFoundException() {
    when(groupReadRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findGroupLeadApplicationId(GROUP_ID))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No linked application group found");
  }

  @Test
  void givenValidGroup_whenFindGroupLeadApplicationId_thenReturnsLeadId() {
    when(groupReadRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(createGroup(GROUP_ID, GROUP_LEAD_ID)));

    var leadId = service.findGroupLeadApplicationId(GROUP_ID);

    assertThat(leadId).isEqualTo(GROUP_LEAD_ID);
  }

  private WorkListItemReadModel createWorkItem(UUID id, UUID assigneeId, long version) {
    var workItem = new WorkListItemReadModel();
    workItem.setId(id);
    workItem.setAssigneeId(assigneeId);
    workItem.setAssignmentVersion(version);
    return workItem;
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
