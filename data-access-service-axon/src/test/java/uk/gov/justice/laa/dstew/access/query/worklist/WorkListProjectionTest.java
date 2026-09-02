package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssigned;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMemberWorkItemChanged;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class WorkListProjectionTest {
  private WorkListItemReadRepository items;
  private ApplicationDataStore applicationDataStore;
  private WorkListProjection projection;

  @BeforeEach
  void setUp() {
    items = mock(WorkListItemReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    projection = new WorkListProjection(items, applicationDataStore);
  }

  @Test
  void givenManualApplication_whenHandled_thenCreatesUnassignedApplicationWorkItem() {
    UUID applicationId = UUID.randomUUID();

    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    Proceeding proceeding = mock(Proceeding.class);
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(data);
    when(data.laaReference()).thenReturn("LAA-123456");
    when(data.usedDelegatedFunctions()).thenReturn(true);
    when(data.categoryOfLaw()).thenReturn("FAMILY");
    when(data.proceedings()).thenReturn(java.util.List.of(proceeding));
    when(proceeding.getMatterType()).thenReturn("SPECIAL_CHILDREN_ACT");

    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 5L, occurredAt), message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    WorkListItemReadModel row = captor.getValue();
    assertThat(row.getApplicationId()).isEqualTo(applicationId);
    assertThat(row.getParentApplicationId()).isNull();
    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getLaaReference()).isEqualTo("LAA-123456");
    assertThat(row.getUsedDelegatedFunctions()).isTrue();
    assertThat(row.getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(row.getMatterTypes()).containsExactly("SPECIAL_CHILDREN_ACT");
    assertThat(row.getApplicationStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentVersion()).isZero();
    assertThat(row.getItemVersion()).isEqualTo(3L);
    assertThat(row.getSubmittedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenCreatedPriorAuthority_whenHandled_thenCreatesDirectWorkUnderItsParent() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    projection.on(
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            0L,
            "fingerprint",
            "PENDING",
            1,
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getParentApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getAssignmentBoundaryId()).isEqualTo(submissionId);
    assertThat(captor.getValue().getAssignmentVersion()).isZero();
    assertThat(captor.getValue().getSubmittedAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
  }

  @Test
  void givenWorkListQuery_whenHandled_thenPagesWithOldestSubmissionFirst() {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            Instant.parse("2026-08-28T10:00:00Z"),
            1L,
            0L);
    when(items.findAll(
            org.mockito.ArgumentMatchers.<Specification<WorkListItemReadModel>>any(),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of(item)));

    FindWorkListItemsResult result =
        projection.handle(new FindWorkListItemsQuery(null, null, null, null, null));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(items)
        .findAll(
            org.mockito.ArgumentMatchers.<Specification<WorkListItemReadModel>>any(),
            pageable.capture());
    assertThat(result.items()).containsExactly(item);
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("submittedAt").getDirection())
        .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
  }

  @Test
  void givenActiveApplication_whenAssigned_thenUpdatesOnlyItsExistingWorkRow() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, applicationId, null, Instant.now(), 1L, 0L);
    when(items.findById(new WorkListItemId(WorkItemType.APPLICATION, applicationId)))
        .thenReturn(Optional.of(row));

    projection.on(
        new ApplicationAssignedToCaseworkerEvent(
            applicationId, 2L, 3L, caseworkerId, Instant.now()),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getItemVersion()).isEqualTo(2L);
    verify(items).save(row);
  }

  @Test
  void givenActiveWorkItem_whenGenericallyAssigned_thenUpdatesItsAssignmentVersion() {
    UUID applicationId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, applicationId, null, Instant.now(), 1L, 0L);
    when(items.findById(new WorkListItemId(WorkItemType.APPLICATION, applicationId)))
        .thenReturn(Optional.of(row));

    projection.on(
        new WorkItemAssigned(
            new WorkItemId(WorkItemType.APPLICATION, applicationId),
            applicationId,
            null,
            2L,
            2L,
            1L,
            UUID.randomUUID(),
            Instant.now()),
        message());

    assertThat(row.getAssignmentVersion()).isEqualTo(1L);
  }

  @Test
  void
      givenFinalApplicationDecision_whenReplayed_thenDeletesOnlyItsApplicationWorkItemIdempotently() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(applicationId, 2L, 3L, "REFUSED", null, Instant.now());

    projection.on(event);
    projection.on(event);

    verify(items, times(2)).deleteByIdItemTypeAndIdItemId(WorkItemType.APPLICATION, applicationId);
    verify(items, never())
        .deleteByIdItemTypeAndIdItemId(WorkItemType.PRIOR_AUTHORITY, applicationId);
  }

  @Test
  void givenActiveLinkedMember_whenGroupAssignmentHandled_thenUpdatesOnlyTheEventMemberSet() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, applicationId, null, Instant.now(), 1L, 0L);
    when(items.findById(new WorkListItemId(WorkItemType.APPLICATION, applicationId)))
        .thenReturn(Optional.of(row));

    projection.on(
        new LinkedGroupMemberWorkItemChanged(
            groupId, applicationId, true, 1L, 0L, null, Instant.now()),
        message());
    projection.on(
        new LinkedGroupAssigned(
            groupId, java.util.List.of(applicationId), 1L, 1L, caseworkerId, Instant.now()),
        message());

    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    assertThat(row.getGroupId()).isEqualTo(groupId);
    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getAssignmentVersion()).isEqualTo(1L);
  }

  @Test
  void givenReset_whenHandled_thenDeletesTheDisposableProjection() {
    projection.reset();

    verify(items).deleteAllInBatch();
  }

  private static EventMessage message() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }
}
