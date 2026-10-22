package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.groupassignment.AssignmentChange;
import uk.gov.justice.laa.dstew.access.query.groupassignment.LinkedGroupAssignmentTracker;

class WorkListProjectionTest {
  private WorkListItemReadRepository items;
  private ApplicationDataStore applicationDataStore;
  private PriorAuthorityDataStore priorAuthorityDataStore;
  private LinkedGroupAssignmentTracker assignmentTracker;
  private WorkListGroupAssignmentStore assignmentStore;
  private WorkListProjection projection;

  @BeforeEach
  void setUp() {
    items = mock(WorkListItemReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    priorAuthorityDataStore = mock(PriorAuthorityDataStore.class);
    assignmentTracker = mock(LinkedGroupAssignmentTracker.class);
    assignmentStore = mock(WorkListGroupAssignmentStore.class);
    Mockito.when(
            assignmentTracker.applicationReady(Mockito.any(), Mockito.any(), Mockito.anyLong()))
        .thenAnswer(
            invocation ->
                new AssignmentChange(
                    invocation.getArgument(1), null, null, null, invocation.getArgument(2)));
    Mockito.when(
            assignmentTracker.directAssignmentChanged(
                Mockito.any(), Mockito.any(), Mockito.nullable(UUID.class), Mockito.anyLong()))
        .thenAnswer(
            invocation ->
                new AssignmentChange(
                    invocation.getArgument(1),
                    null,
                    null,
                    invocation.getArgument(2),
                    invocation.getArgument(3)));
    Mockito.when(
            assignmentTracker.groupAssignmentChanged(
                Mockito.any(), Mockito.any(), Mockito.nullable(UUID.class), Mockito.anyLong()))
        .thenReturn(List.of());
    Mockito.when(
            assignmentTracker.memberInherited(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong()))
        .thenReturn(Optional.empty());
    Mockito.when(
            assignmentTracker.assignmentReclaimed(
                Mockito.any(), Mockito.any(), Mockito.nullable(UUID.class), Mockito.anyLong()))
        .thenReturn(Optional.empty());
    projection =
        new WorkListProjection(
            items,
            applicationDataStore,
            priorAuthorityDataStore,
            assignmentTracker,
            assignmentStore);
  }

  @Test
  void givenApplicationReady_whenProjected_thenReadyAtIsReadinessEventTime() {
    UUID applicationId = UUID.randomUUID();

    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    Proceeding proceeding = mock(Proceeding.class);
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(data);
    when(data.laaReference()).thenReturn("LAA-123456");
    when(data.usedDelegatedFunctions()).thenReturn(true);
    when(data.categoryOfLaw()).thenReturn("FAMILY");
    when(data.proceedings()).thenReturn(List.of(proceeding));
    when(proceeding.getMatterType()).thenReturn("special children act (SCA)");

    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 5L, 0L, occurredAt),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    WorkListItemReadModel row = captor.getValue();
    assertThat(row.getId()).isEqualTo(applicationId);
    assertThat(row.getItemType()).isEqualTo(WorkItemType.APPLICATION);
    assertThat(row.getParentApplicationId()).isNull();
    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getLaaReference()).isEqualTo("LAA-123456");
    assertThat(row.getUsedDelegatedFunctions()).isTrue();
    assertThat(row.getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(row.getMatterTypes()).containsExactly("special children act (SCA)");
    assertThat(row.getApplicationStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentVersion()).isZero();
    assertThat(row.getItemVersion()).isEqualTo(3L);
    assertThat(row.getSubmittedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenGroupedReadyApplication_whenProjected_thenRowUsesGroupBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(data);
    when(assignmentTracker.applicationReady(assignmentStore, applicationId, 4L))
        .thenReturn(new AssignmentChange(applicationId, groupId, null, null, 4L));

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 5L, 4L, occurredAt),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    WorkListItemReadModel row = captor.getValue();
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    assertThat(row.getGroupId()).isEqualTo(groupId);
    assertThat(row.getAssignmentVersion()).isEqualTo(4L);
    verify(assignmentTracker).applicationReady(assignmentStore, applicationId, 4L);
  }

  @Test
  void givenPriorAuthoritySubmitted_whenProjected_thenReadyAtIsSubmissionEventTime() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload parentData = mock(ApplicationDataPayload.class);
    Proceeding proceeding = mock(Proceeding.class);
    PriorAuthorityDataPayload priorAuthorityData = mock(PriorAuthorityDataPayload.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent content =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails expertDetails =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails.class);
    when(applicationDataStore.get(applicationId, 7L)).thenReturn(parentData);
    when(parentData.laaReference()).thenReturn("LAA-654321");
    when(parentData.categoryOfLaw()).thenReturn("FAMILY");
    when(parentData.categoryOfLawCode()).thenReturn("MAT");
    when(parentData.proceedings()).thenReturn(List.of(proceeding));
    when(proceeding.getMatterType()).thenReturn("special children act (SCA)");
    when(proceeding.getMatterTypeCode()).thenReturn("KPBLW");
    when(priorAuthorityDataStore.get(priorAuthorityId, 0L)).thenReturn(priorAuthorityData);
    when(priorAuthorityData.content()).thenReturn(content);
    when(content.priorAuthorityType())
        .thenReturn(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT);
    when(content.expertDetails()).thenReturn(expertDetails);
    when(expertDetails.expertType()).thenReturn("Pathologist");

    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId,
            applicationId,
            "type",
            1,
            0,
            7L,
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getParentApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getAssignmentBoundaryId()).isEqualTo(priorAuthorityId);
    assertThat(captor.getValue().getAssignmentVersion()).isZero();
    assertThat(captor.getValue().getSubmittedAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
    assertThat(captor.getValue().getLaaReference()).isEqualTo("LAA-654321");
    assertThat(captor.getValue().getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(captor.getValue().getMatterTypes()).containsExactly("special children act (SCA)");
    assertThat(captor.getValue().getCategoryOfLawCode()).isEqualTo("MAT");
    assertThat(captor.getValue().getMatterTypeCodes()).containsExactly("KPBLW");
    assertThat(captor.getValue().getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(captor.getValue().getExpertType()).isEqualTo("Pathologist");
  }

  @Test
  void givenPriorAuthority_whenProjected_thenUsesParentMatterPairs() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload parentData = mock(ApplicationDataPayload.class);
    PriorAuthorityDataPayload priorAuthorityData = mock(PriorAuthorityDataPayload.class);
    PriorAuthorityContent content = mock(PriorAuthorityContent.class);
    when(applicationDataStore.get(applicationId, 7L)).thenReturn(parentData);
    when(parentData.categoryOfLaw()).thenReturn("Family");
    when(parentData.categoryOfLawCode()).thenReturn("MAT");
    when(parentData.proceedings()).thenReturn(List.of(proceeding("Children Act", "KPBLW")));
    when(priorAuthorityDataStore.get(priorAuthorityId, 0L)).thenReturn(priorAuthorityData);
    when(priorAuthorityData.content()).thenReturn(content);
    when(content.priorAuthorityType()).thenReturn(PriorAuthorityType.COUNSEL);

    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId,
            applicationId,
            "type",
            1,
            0,
            7L,
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getCategoryOfLaw()).isEqualTo("Family");
    assertThat(captor.getValue().getCategoryOfLawCode()).isEqualTo("MAT");
    assertThat(captor.getValue().getMatterTypes()).containsExactly("Children Act");
    assertThat(captor.getValue().getMatterTypeCodes()).containsExactly("KPBLW");
  }

  @Test
  void givenRepeatedMatterPair_whenProjected_thenKeepsAlignedDistinctPairs() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    List<Proceeding> proceedings =
        List.of(
            proceeding("Children Act", "KPBLW"),
            proceeding("Children Act", "KPBLW"),
            proceeding("Children Act", "ALT"));
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(data);
    when(data.proceedings()).thenReturn(proceedings);
    when(data.categoryOfLaw()).thenReturn("Family");
    when(data.categoryOfLawCode()).thenReturn("MAT");

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(
            applicationId, 3L, 5L, 0L, Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getMatterTypes()).containsExactly("Children Act", "Children Act");
    assertThat(captor.getValue().getMatterTypeCodes()).containsExactly("KPBLW", "ALT");
    assertThat(captor.getValue().getCategoryOfLawCode()).isEqualTo("MAT");
  }

  @Test
  void givenNonExpertPriorAuthority_whenHandled_thenStoresNoExpertType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload parentData = mock(ApplicationDataPayload.class);
    PriorAuthorityDataPayload priorAuthorityData = mock(PriorAuthorityDataPayload.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent content =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent.class);
    when(applicationDataStore.get(applicationId, 4L)).thenReturn(parentData);
    when(priorAuthorityDataStore.get(priorAuthorityId, 1)).thenReturn(priorAuthorityData);
    when(priorAuthorityData.content()).thenReturn(content);
    when(content.priorAuthorityType())
        .thenReturn(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.COUNSEL);

    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 0, 1, 4L, Instant.now()),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getPriorAuthorityType()).isEqualTo("COUNSEL");
    assertThat(captor.getValue().getExpertType()).isNull();
  }

  @Test
  void givenManualApplicationWithoutProceedings_whenHandled_thenStoresNoMatterTypes() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(data);
    when(data.proceedings()).thenReturn(null);

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(
            applicationId, 3L, 5L, 0L, Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getMatterTypes()).isEmpty();
  }

  @Test
  void givenWorkListQuery_whenHandled_thenPagesWithOldestReadyTimeFirst() {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            UUID.randomUUID(),
            null,
            Instant.parse("2026-08-28T10:00:00Z"),
            1L,
            0L);
    when(items.findAll(
            ArgumentMatchers.<Specification<WorkListItemReadModel>>any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(item)));

    FindWorkListItemsResult result =
        projection.handle(new FindWorkListItemsQuery(null, null, null, null, null, null));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(items)
        .findAll(ArgumentMatchers.<Specification<WorkListItemReadModel>>any(), pageable.capture());
    assertThat(result.items()).containsExactly(item);
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("submittedAt").getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }

  private Proceeding proceeding(String name, String code) {
    return Proceeding.builder()
        .id(UUID.randomUUID())
        .leadProceeding(false)
        .code("SE003")
        .description("Proceeding")
        .matterType(name)
        .matterTypeCode(code)
        .build();
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
            applicationId, WorkItemType.APPLICATION, 2L, 1L, caseworkerId, occurredAt),
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
        new WorkItemUnassigned(itemId, WorkItemType.APPLICATION, 2L, 2L, occurredAt), message());

    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getItemVersion()).isEqualTo(2L);
    assertThat(row.getAssignmentVersion()).isEqualTo(2L);
    assertThat(row.getUpdatedAt()).isEqualTo(occurredAt);
    assertThat(row.getProjectionPosition()).isNotZero();
    verify(items).save(row);
    verify(assignmentTracker).directAssignmentChanged(assignmentStore, itemId, null, 2L);
  }

  @ParameterizedTest
  @EnumSource(WorkItemType.class)
  void givenAssignment_whenProjected_thenOnlyApplicationsTracked(WorkItemType type) {
    UUID itemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(type, itemId, null, Instant.now(), 1L, 0L);
    when(items.findById(itemId)).thenReturn(Optional.of(row));

    projection.on(
        new WorkItemAssigned(itemId, type, 2L, 3L, caseworkerId, Instant.now()), message());

    if (type == WorkItemType.APPLICATION) {
      verify(assignmentTracker).directAssignmentChanged(assignmentStore, itemId, caseworkerId, 3L);
    } else {
      verify(assignmentTracker, never()).directAssignmentChanged(any(), any(), any(), anyLong());
    }
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
              itemId, WorkItemType.APPLICATION, 1L, 1L, UUID.randomUUID(), Instant.now()),
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
    verify(assignmentTracker)
        .directAssignmentChanged(
            ArgumentMatchers.eq(assignmentStore),
            ArgumentMatchers.eq(itemId),
            ArgumentMatchers.any(UUID.class),
            ArgumentMatchers.eq(1L));
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
          new WorkItemUnassigned(itemId, WorkItemType.APPLICATION, 1L, 1L, Instant.now()),
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
        new ApplicationDecisionMadeEvent(applicationId, 2L, 3L, "REFUSED", null, Instant.now());

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
            2L,
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
    verify(assignmentStore).deleteAll();
  }

  @Test
  void givenProjection_whenInspected_thenProcessesInOneLane() {
    assertThat(WorkListProjection.class.isAnnotationPresent(SequencingPolicy.class)).isTrue();
  }

  @Test
  void givenGroupAssignment_whenProjected_thenRowUsesGroupAssignmentBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    AssignmentChange change = new AssignmentChange(applicationId, groupId, null, caseworkerId, 4L);
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, caseworkerId, 4L))
        .thenReturn(List.of(change));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));
    LinkedGroupAssignedEvent event =
        new LinkedGroupAssignedEvent(groupId, caseworkerId, 4L, occurredAt);

    projection.on(event, message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getAssignmentVersion()).isEqualTo(4L);
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    assertThat(row.getGroupId()).isEqualTo(groupId);
    assertThat(row.getUpdatedAt()).isEqualTo(occurredAt);
    verify(items).save(row);
  }

  @Test
  void givenGroupUnassigned_whenProjected_thenRowCaseworkerIsCleared() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    row.applyAssignment(groupId, caseworkerId, 2L);
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, null, 3L))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, caseworkerId, null, 3L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(new LinkedGroupUnassignedEvent(groupId, 3L, occurredAt), message());

    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentVersion()).isEqualTo(3L);
    verify(items).save(row);
  }

  @Test
  void givenChangeForMissingRow_whenProjected_thenNothingSaved() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, caseworkerId, 4L))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, null, caseworkerId, 4L)));

    projection.on(
        new LinkedGroupAssignedEvent(
            groupId, caseworkerId, 4L, Instant.parse("2026-09-30T12:00:00Z")),
        message());

    verify(items).findById(applicationId);
    verify(items, never()).save(any());
  }

  @Test
  void givenChangeForPriorAuthorityRow_whenProjected_thenNothingSaved() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel priorAuthority =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY, applicationId, UUID.randomUUID(), Instant.now(), 1L, 0L);
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, caseworkerId, 4L))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, null, caseworkerId, 4L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(priorAuthority));

    projection.on(
        new LinkedGroupAssignedEvent(
            groupId, caseworkerId, 4L, Instant.parse("2026-09-30T12:00:00Z")),
        message());

    assertThat(priorAuthority.getAssigneeId()).isNull();
    verify(items, never()).save(any());
  }

  @Test
  void givenDecision_whenProjected_thenTrackerMarksInactive() {
    UUID applicationId = UUID.randomUUID();

    projection.on(
        new ApplicationDecisionMadeEvent(
            applicationId, 2L, 3L, "GRANTED", null, Instant.parse("2026-09-30T12:00:00Z")));

    verify(items).deleteById(applicationId);
    verify(assignmentTracker).applicationDecided(assignmentStore, applicationId);
  }

  @Test
  void givenInheritedAssignment_whenProjected_thenRowShowsGroupOwner() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    when(assignmentTracker.memberInherited(
            assignmentStore, groupId, applicationId, caseworkerId, 2L))
        .thenReturn(
            Optional.of(new AssignmentChange(applicationId, groupId, null, caseworkerId, 2L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new LinkedGroupMemberAssignmentInheritedEvent(
            groupId, applicationId, caseworkerId, 2L, occurredAt),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    verify(items).save(row);
  }

  @Test
  void givenReclaimedAssignment_whenProjected_thenRowReturnsToDirectBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    row.applyAssignment(groupId, null, 2L);
    when(assignmentTracker.assignmentReclaimed(assignmentStore, applicationId, caseworkerId, 4L))
        .thenReturn(Optional.of(new AssignmentChange(applicationId, null, null, caseworkerId, 4L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new ApplicationAssignmentReclaimedFromGroupEvent(
            applicationId, groupId, caseworkerId, 4L, occurredAt),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(applicationId);
    assertThat(row.getGroupId()).isNull();
    verify(items).save(row);
  }

  @Test
  void givenGroupCreated_whenProjected_thenActiveRowsJoinGroupBoundary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    when(assignmentTracker.groupCreated(assignmentStore, groupId, List.of(applicationId)))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, null, null, 0L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, applicationId, List.of(applicationId), occurredAt),
        message());

    assertThat(row.getAssignmentBoundaryType()).isEqualTo("LINKED_GROUP");
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    verify(items).save(row);
  }

  @Test
  void givenMemberAdded_whenProjected_thenNewMemberTakesGroupAssignment() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, null, occurredAt, 3L, 1L);
    when(assignmentTracker.memberAdded(assignmentStore, groupId, applicationId))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, null, caseworkerId, 2L)));
    when(items.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new MemberAddedToGroupEvent(groupId, UUID.randomUUID(), applicationId, occurredAt),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getAssignmentBoundaryId()).isEqualTo(groupId);
    assertThat(row.getAssignmentVersion()).isEqualTo(2L);
    verify(items).save(row);
  }

  @Test
  void givenMemberRemoved_whenProjected_thenTrackerReleasesMember() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");

    projection.on(
        new MemberRemovedFromGroupEvent(
            groupId, UUID.randomUUID(), applicationId, 1L, null, 0L, occurredAt));

    verify(assignmentTracker).membersLeft(assignmentStore, groupId, List.of(applicationId), false);
  }

  @Test
  void givenGroupDissolved_whenProjected_thenTrackerReleasesAllMembers() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    List<UUID> memberIds = List.of(firstId, secondId);
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");

    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            groupId, firstId, secondId, memberIds, 1L, null, 0L, occurredAt));

    verify(assignmentTracker).membersLeft(assignmentStore, groupId, memberIds, true);
  }

  private static EventMessage message() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }
}
