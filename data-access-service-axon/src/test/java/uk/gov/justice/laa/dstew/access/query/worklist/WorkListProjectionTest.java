package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaries;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;

class WorkListProjectionTest {
  private WorkListItemReadRepository items;
  private WorkListGroupMemberReadRepository groupMembers;
  private ApplicationSummaries applicationSummaries;
  private WorkListProjection projection;

  @BeforeEach
  void setUp() {
    items = mock(WorkListItemReadRepository.class);
    groupMembers = mock(WorkListGroupMemberReadRepository.class);
    applicationSummaries = mock(ApplicationSummaries.class);
    projection = new WorkListProjection(items, groupMembers, applicationSummaries);
  }

  @Test
  void givenApplicationCreated_whenProjected_thenCreatesPendingWorkItemAtSubmissionTime() {
    UUID applicationId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-08-28T09:00:00Z");
    projection.on(
        new ApplicationCreatedEvent(
            applicationId,
            "fingerprint",
            "APPLICATION_SUBMITTED",
            1,
            submittedAt,
            List.of(),
            "1A",
            null,
            List.of(),
            (ParsedAppContentDetails) null,
            (String) null),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    WorkListItemReadModel row = captor.getValue();
    assertThat(row.getId()).isEqualTo(applicationId);
    assertThat(row.getItemType()).isEqualTo(WorkItemType.APPLICATION);
    assertThat(row.getParentApplicationId()).isNull();
    assertThat(row.getSubmittedAt()).isEqualTo(submittedAt);
    assertThat(row.getReadyAt()).isNull();
    assertThat(row.getAutoGranted()).isEqualTo(AutoGrantedState.PENDING);
    assertThat(row.getApplicationStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(row.getUpdatedAt()).isEqualTo(submittedAt);
    assertThat(row.getItemVersion()).isZero();
  }

  @Test
  void givenApplicationReady_whenProjected_thenUpdatesTheExistingWorkItem() {
    UUID applicationId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-08-28T09:00:00Z");
    WorkListItemReadModel existing =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, submittedAt, 1L, 0L);
    existing.setAutoGranted(AutoGrantedState.PENDING);
    existing.setAssigneeId(assigneeId);
    existing.setAssignmentVersion(4L);
    when(items.findById(applicationId)).thenReturn(Optional.of(existing));

    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, occurredAt), message());

    WorkListItemReadModel row = existing;
    assertThat(row.getId()).isEqualTo(applicationId);
    assertThat(row.getItemType()).isEqualTo(WorkItemType.APPLICATION);
    assertThat(row.getParentApplicationId()).isNull();
    assertThat(row.getAssigneeId()).isEqualTo(assigneeId);
    assertThat(row.getApplicationStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentVersion()).isEqualTo(4L);
    assertThat(row.getItemVersion()).isEqualTo(3L);
    assertThat(row.getSubmittedAt()).isEqualTo(submittedAt);
    assertThat(row.getReadyAt()).isEqualTo(occurredAt);
    assertThat(row.getAutoGranted()).isEqualTo(AutoGrantedState.MANUAL);
    assertThat(row.getAssignmentVersion()).isEqualTo(4L);
    verify(items).save(existing);
  }

  @Test
  void givenPriorAuthoritySubmitted_whenProjected_thenReadyAtIsSubmissionEventTime() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1,
            "Pathologist",
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getParentApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getAssignmentBoundaryId()).isEqualTo(priorAuthorityId);
    assertThat(captor.getValue().getAssignmentVersion()).isZero();
    assertThat(captor.getValue().getSubmittedAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
    assertThat(captor.getValue().getReadyAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
    assertThat(captor.getValue().getAutoGranted()).isNull();
    assertThat(captor.getValue().getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(captor.getValue().getExpertType()).isEqualTo("Pathologist");
  }

  @Test
  void givenNonExpertPriorAuthority_whenHandled_thenStoresNoExpertType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "COUNSEL", 0, "Pathologist", Instant.now()),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getPriorAuthorityType()).isEqualTo("COUNSEL");
    assertThat(captor.getValue().getExpertType()).isNull();
  }

  @Test
  void givenMissingApplicationWorkItem_whenManualReadinessIsProjected_thenFailsWithoutSaving() {
    UUID applicationId = UUID.randomUUID();
    when(items.findById(applicationId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                projection.on(
                    new ApplicationReadyForManualAssessmentEvent(
                        applicationId, 3L, Instant.parse("2026-08-28T10:00:00Z")),
                    message()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing application work-list item for " + applicationId);

    verify(items, never()).save(any());
  }

  @Test
  void givenWorkListQuery_whenHandled_thenPagesWithOldestSubmissionTimeFirst() {
    UUID applicationId = UUID.randomUUID();
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            applicationId,
            null,
            Instant.parse("2026-08-28T10:00:00Z"),
            1L,
            0L);
    WorkListItemReadModel priorAuthority =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            UUID.randomUUID(),
            applicationId,
            Instant.parse("2026-08-28T11:00:00Z"),
            1L,
            0L);
    when(items.findAll(
            ArgumentMatchers.<Specification<WorkListItemReadModel>>any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(item, priorAuthority)));
    ApplicationSummaryReadModel summary =
        ApplicationSummaryReadModel.builder().applicationId(applicationId).build();
    when(applicationSummaries.findByIds(List.of(applicationId)))
        .thenReturn(Map.of(applicationId, summary));

    FindWorkListItemsResult result =
        projection.handle(new FindWorkListItemsQuery(null, null, null, null, null, null));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(items)
        .findAll(ArgumentMatchers.<Specification<WorkListItemReadModel>>any(), pageable.capture());
    assertThat(result.items()).containsExactly(item, priorAuthority);
    assertThat(result.applicationSummaries()).containsExactly(Map.entry(applicationId, summary));
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("submittedAt").getDirection())
        .isEqualTo(Sort.Direction.ASC);
    assertThat(pageable.getValue().getSort().stream().map(Sort.Order::getProperty))
        .containsExactly("submittedAt", "itemType", "id");
  }

  @Test
  void givenActiveWorkItem_whenGenericallyAssigned_thenUpdatesItsAssignmentVersion() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, Instant.now(), 1L, 0L);
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new WorkItemAssigned(
            applicationId,
            WorkItemType.APPLICATION,
            applicationId,
            2L,
            1L,
            caseworkerId,
            occurredAt),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getItemVersion()).isEqualTo(2L);
    assertThat(row.getAssignmentVersion()).isEqualTo(1L);
    assertThat(row.getUpdatedAt()).isEqualTo(occurredAt);
    assertThat(row.getProjectionPosition()).isNotZero();
    verify(items).save(row);
  }

  @Test
  void givenActiveWorkItem_whenGenericallyUnassigned_thenClearsItsAssignmentAndUpdatesMetadata() {
    UUID itemId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(WorkItemType.APPLICATION, itemId, null, occurredAt, 1L, 0L);
    row.setAssigneeId(UUID.randomUUID());
    when(items.findById(itemId)).thenReturn(Optional.of(row));

    projection.on(
        new WorkItemUnassigned(itemId, WorkItemType.APPLICATION, itemId, 2L, 2L, occurredAt),
        message());

    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getItemVersion()).isEqualTo(2L);
    assertThat(row.getAssignmentVersion()).isEqualTo(2L);
    assertThat(row.getUpdatedAt()).isEqualTo(occurredAt);
    assertThat(row.getProjectionPosition()).isNotZero();
    verify(items).save(row);
  }

  @Test
  void givenMissingWorkItem_whenAssigned_thenLogsWarningAndDoesNotSave() {
    UUID itemId = UUID.randomUUID();
    when(items.findById(itemId)).thenReturn(Optional.empty());
    Logger logger = (Logger) LoggerFactory.getLogger(WorkListProjection.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      projection.on(
          new WorkItemAssigned(
              itemId, WorkItemType.APPLICATION, itemId, 1L, 1L, UUID.randomUUID(), Instant.now()),
          message());

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .isEqualTo(
                          "Cannot assign missing work-list item: id="
                              + itemId
                              + ", type=APPLICATION"));
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }

    verify(items, never()).save(any());
  }

  @Test
  void givenMissingWorkItem_whenUnassigned_thenLogsWarningAndDoesNotSave() {
    UUID itemId = UUID.randomUUID();
    when(items.findById(itemId)).thenReturn(Optional.empty());
    Logger logger = (Logger) LoggerFactory.getLogger(WorkListProjection.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      projection.on(
          new WorkItemUnassigned(itemId, WorkItemType.APPLICATION, itemId, 1L, 1L, Instant.now()),
          message());

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getFormattedMessage())
                      .isEqualTo(
                          "Cannot unassign missing work-list item: id="
                              + itemId
                              + ", type=APPLICATION"));
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }

    verify(items, never()).save(any());
  }

  @Test
  void givenMismatchedWorkItemType_whenGenericEventArrives_thenFailsFast() {
    UUID itemId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(WorkItemType.APPLICATION, itemId, null, Instant.now(), 1L, 0L);
    when(items.findById(itemId)).thenReturn(Optional.of(row));

    assertThatThrownBy(
            () ->
                projection.on(
                    new WorkItemAssigned(
                        itemId,
                        WorkItemType.PRIOR_AUTHORITY,
                        UUID.randomUUID(),
                        1L,
                        1L,
                        UUID.randomUUID(),
                        Instant.now()),
                    message()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Work item type mismatch for " + itemId);
  }

  @Test
  void
      givenFinalApplicationDecision_whenReplayed_thenDeletesOnlyItsApplicationWorkItemIdempotently() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(applicationId, 2L, "REFUSED", null, Instant.now());

    projection.on(event);
    projection.on(event);

    verify(items, times(2)).deleteById(applicationId);
  }

  @Test
  void givenFinalPriorAuthorityDecision_whenReplayed_thenDeletesItsWorkItemIdempotently() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDecisionMadeEvent event =
        new PriorAuthorityDecisionMadeEvent(
            priorAuthorityId,
            UUID.randomUUID(),
            "DISBURSEMENT",
            "REFUSED",
            "Recorded",
            BigDecimal.ZERO,
            Instant.now(),
            Instant.now());

    projection.on(event);
    projection.on(event);

    verify(items, times(2)).deleteById(priorAuthorityId);
  }

  @Test
  void givenReset_whenHandled_thenDeletesTheDisposableProjection() {
    projection.reset();

    verify(items).deleteAllInBatch();
    verify(groupMembers).deleteAllInBatch();
  }

  @Test
  void givenLinkedApplication_whenReadyProjected_thenUsesLinkedGroupBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkListItemReadModel existing = applicationRow(applicationId);
    when(items.findById(applicationId)).thenReturn(Optional.of(existing));
    when(groupMembers.findById(applicationId))
        .thenReturn(Optional.of(new WorkListGroupMemberReadModel(applicationId, groupId)));

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, Instant.now()), message());

    verify(items).save(existing);
    assertLinkedGroupBoundary(existing, groupId);
  }

  @Test
  void givenActiveApplications_whenGroupCreated_thenMembersUseLinkedGroupBoundary() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkListItemReadModel lead = applicationRow(leadId);
    WorkListItemReadModel member = applicationRow(memberId);
    when(items.findById(leadId)).thenReturn(Optional.of(lead));
    when(items.findById(memberId)).thenReturn(Optional.of(member));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadId, List.of(leadId, memberId), Instant.now()),
        message());

    assertLinkedGroupBoundary(lead, groupId);
    assertLinkedGroupBoundary(member, groupId);
    verify(groupMembers, times(2)).save(any(WorkListGroupMemberReadModel.class));
    verify(items).save(lead);
    verify(items).save(member);
  }

  @Test
  void givenNotYetReadyApplication_whenAddedToGroup_thenRecordsMembershipOnly() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    when(items.findById(memberId)).thenReturn(Optional.empty());

    projection.on(
        new MemberAddedToGroupEvent(groupId, UUID.randomUUID(), memberId, Instant.now()),
        message());

    ArgumentCaptor<WorkListGroupMemberReadModel> captor =
        ArgumentCaptor.forClass(WorkListGroupMemberReadModel.class);
    verify(groupMembers).save(captor.capture());
    assertThat(captor.getValue().getApplicationId()).isEqualTo(memberId);
    assertThat(captor.getValue().getGroupId()).isEqualTo(groupId);
    verify(items, never()).save(any());
  }

  @Test
  void givenGroupedApplication_whenRemovedFromGroup_thenUsesDirectBoundary() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkListItemReadModel member = applicationRow(memberId);
    member.useLinkedGroupBoundary(groupId);
    when(items.findById(memberId)).thenReturn(Optional.of(member));

    projection.on(
        new MemberRemovedFromGroupEvent(groupId, UUID.randomUUID(), memberId, 2L, Instant.now()),
        message());

    assertDirectBoundary(member);
    verify(groupMembers).deleteById(memberId);
    verify(items).save(member);
  }

  @Test
  void givenGroupedApplications_whenGroupDissolved_thenEveryMemberUsesDirectBoundary() {
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkListItemReadModel lead = applicationRow(leadId);
    lead.useLinkedGroupBoundary(groupId);
    WorkListItemReadModel removed = applicationRow(removedId);
    removed.useLinkedGroupBoundary(groupId);
    when(items.findById(leadId)).thenReturn(Optional.of(lead));
    when(items.findById(removedId)).thenReturn(Optional.of(removed));
    when(groupMembers.findAllByGroupId(groupId))
        .thenReturn(List.of(new WorkListGroupMemberReadModel(leadId, groupId)));

    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            groupId, leadId, removedId, List.of(leadId), 1L, Instant.now()),
        message());

    assertDirectBoundary(lead);
    assertDirectBoundary(removed);
    verify(groupMembers).deleteById(leadId);
    verify(groupMembers).deleteById(removedId);
  }

  @Test
  void givenGroupedPriorAuthorityId_whenGroupCreated_thenLeavesPriorAuthorityDirect() {
    UUID priorAuthorityId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            priorAuthorityId,
            UUID.randomUUID(),
            Instant.now(),
            1L,
            0L);
    when(items.findById(priorAuthorityId)).thenReturn(Optional.of(row));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            UUID.randomUUID(), priorAuthorityId, List.of(priorAuthorityId), Instant.now()),
        message());

    assertDirectBoundary(row);
    verify(items, never()).save(any());
  }

  private static WorkListItemReadModel applicationRow(UUID applicationId) {
    return new WorkListItemReadModel(
        WorkItemType.APPLICATION, applicationId, null, Instant.now(), 1L, 0L);
  }

  private static void assertLinkedGroupBoundary(WorkListItemReadModel row, UUID groupId) {
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    assertThat(row.getGroupId()).isEqualTo(groupId);
  }

  private static void assertDirectBoundary(WorkListItemReadModel row) {
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(row.getId());
    assertThat(row.getGroupId()).isNull();
  }

  private static EventMessage message() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }
}
