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
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

class WorkListProjectionTest {
  private WorkListItemReadRepository items;
  private ApplicationDataStore applicationDataStore;
  private PriorAuthorityDataStore priorAuthorityDataStore;
  private WorkListProjection projection;

  @BeforeEach
  void setUp() {
    items = mock(WorkListItemReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    priorAuthorityDataStore = mock(PriorAuthorityDataStore.class);
    projection = new WorkListProjection(items, applicationDataStore, priorAuthorityDataStore);
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
    when(data.proceedings()).thenReturn(List.of(proceeding));
    when(proceeding.getMatterType()).thenReturn("SPECIAL_CHILDREN_ACT");

    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");
    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 5L, occurredAt), message());

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
    assertThat(row.getMatterTypes()).containsExactly("SPECIAL_CHILDREN_ACT");
    assertThat(row.getApplicationStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getAssignmentVersion()).isZero();
    assertThat(row.getItemVersion()).isEqualTo(3L);
    assertThat(row.getSubmittedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenExpertPriorAuthority_whenHandled_thenCreatesWorkWithParentSnapshotAndExpertType() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload parentData = mock(ApplicationDataPayload.class);
    Proceeding proceeding = mock(Proceeding.class);
    PriorAuthorityDataPayload priorAuthorityData = mock(PriorAuthorityDataPayload.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent content =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails expertDetails =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails.class);
    when(applicationDataStore.getLatest(applicationId)).thenReturn(parentData);
    when(parentData.laaReference()).thenReturn("LAA-654321");
    when(parentData.categoryOfLaw()).thenReturn("FAMILY");
    when(parentData.proceedings()).thenReturn(List.of(proceeding));
    when(proceeding.getMatterType()).thenReturn("SPECIAL_CHILDREN_ACT");
    when(priorAuthorityDataStore.get(submissionId, 0L)).thenReturn(priorAuthorityData);
    when(priorAuthorityData.content()).thenReturn(content);
    when(content.priorAuthorityType())
        .thenReturn(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT);
    when(content.expertDetails()).thenReturn(expertDetails);
    when(expertDetails.expertType()).thenReturn("Pathologist");

    projection.on(
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "COUNSEL",
            0L,
            "fingerprint",
            "PENDING",
            1,
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getParentApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getAssignmentBoundaryId()).isEqualTo(submissionId);
    assertThat(captor.getValue().getAssignmentVersion()).isZero();
    assertThat(captor.getValue().getSubmittedAt()).isEqualTo(Instant.parse("2026-08-28T10:00:00Z"));
    assertThat(captor.getValue().getLaaReference()).isEqualTo("LAA-654321");
    assertThat(captor.getValue().getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(captor.getValue().getMatterTypes()).containsExactly("SPECIAL_CHILDREN_ACT");
    assertThat(captor.getValue().getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(captor.getValue().getExpertType()).isEqualTo("Pathologist");
  }

  @Test
  void givenNonExpertPriorAuthority_whenHandled_thenStoresNoExpertType() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload parentData = mock(ApplicationDataPayload.class);
    PriorAuthorityDataPayload priorAuthorityData = mock(PriorAuthorityDataPayload.class);
    uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent content =
        mock(uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent.class);
    when(applicationDataStore.getLatest(applicationId)).thenReturn(parentData);
    when(priorAuthorityDataStore.get(submissionId, 0L)).thenReturn(priorAuthorityData);
    when(priorAuthorityData.content()).thenReturn(content);
    when(content.priorAuthorityType())
        .thenReturn(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.COUNSEL);

    projection.on(
        new PriorAuthorityCreatedEvent(
            submissionId, applicationId, "EXPERT", 0L, "fingerprint", "PENDING", 1, Instant.now()),
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
            applicationId, 3L, 5L, Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor =
        ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getMatterTypes()).isEmpty();
  }

  @Test
  void givenWorkListQuery_whenHandled_thenPagesWithOldestSubmissionFirst() {
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
        projection.handle(new FindWorkListItemsQuery(null, null, null, null, null));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(items)
        .findAll(ArgumentMatchers.<Specification<WorkListItemReadModel>>any(), pageable.capture());
    assertThat(result.items()).containsExactly(item);
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
    assertThat(pageable.getValue().getSort().getOrderFor("submittedAt").getDirection())
        .isEqualTo(Sort.Direction.ASC);
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
  void givenReset_whenHandled_thenDeletesTheDisposableProjection() {
    projection.reset();

    verify(items).deleteAllInBatch();
  }

  private static EventMessage message() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }
}
