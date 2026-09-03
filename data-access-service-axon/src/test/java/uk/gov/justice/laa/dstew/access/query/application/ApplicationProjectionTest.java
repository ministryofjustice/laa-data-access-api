package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;

class ApplicationProjectionTest {

  private ApplicationReadRepository applicationReadRepository;
  private LinkedApplicationGroupReadRepository groupReadRepository;
  private QueryUpdateEmitter queryUpdateEmitter;
  private ApplicationDataStore applicationDataStore;
  private ApplicationListIndexReadRepository listIndexRepository;
  private ApplicationProjection projection;

  @BeforeEach
  void setUp() {
    applicationReadRepository = mock(ApplicationReadRepository.class);
    groupReadRepository = mock(LinkedApplicationGroupReadRepository.class);
    queryUpdateEmitter = mock(QueryUpdateEmitter.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    listIndexRepository = mock(ApplicationListIndexReadRepository.class);
    when(applicationDataStore.get(any(), anyLong()))
        .thenAnswer(
            invocation ->
                ApplicationDataPayload.from(applicationCreationDetails(invocation.getArgument(0))));
    projection =
        new ApplicationProjection(
            applicationReadRepository,
            groupReadRepository,
            applicationDataStore,
            listIndexRepository);
  }

  @Test
  void givenCreatedEvent_whenHandled_thenSavesBeforeEmitting() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationReadModel saved =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.save(any())).thenReturn(saved);

    projection.on(event, queryUpdateEmitter);

    InOrder order = inOrder(applicationReadRepository, queryUpdateEmitter);
    order.verify(applicationReadRepository).save(any());
    order
        .verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenCreatedEvent_whenHandled_thenEmittedPredicateMatchesApplicationId() {
    UUID applicationId = UUID.randomUUID();
    final UUID otherId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);

    final Predicate<?>[] capturedPredicate = new Predicate[1];
    doAnswer(
            inv -> {
              capturedPredicate[0] = (Predicate<?>) inv.getArgument(1);
              return null;
            })
        .when(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));

    when(applicationReadRepository.save(any()))
        .thenReturn(ApplicationReadModel.builder().applicationId(applicationId).build());

    projection.on(event, queryUpdateEmitter);

    assertThat(capturedPredicate[0]).isNotNull();
    Predicate<FindApplicationByIdQuery> predicate =
        (Predicate<FindApplicationByIdQuery>) capturedPredicate[0];
    assertThat(predicate.test(new FindApplicationByIdQuery(applicationId))).isTrue();
    assertThat(predicate.test(new FindApplicationByIdQuery(otherId))).isFalse();
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllProjections() {
    projection.reset();

    verify(applicationReadRepository).deleteAllInBatch();
  }

  @Test
  void givenSubmittedApplications_whenReconciliationQueries_thenReturnsOnlyOldUnassessedOnes() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID stalledId = UUID.randomUUID();
    UUID assessedId = UUID.randomUUID();
    UUID recentId = UUID.randomUUID();
    ApplicationReadModel stalled = reconciliationReadModel(stalledId);
    ApplicationReadModel assessed = reconciliationReadModel(assessedId);
    ApplicationReadModel recent = reconciliationReadModel(recentId);
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(stalled, assessed, recent));
    ApplicationDataPayload stalledData =
        reconciliationData(threshold.minus(15, ChronoUnit.MINUTES), null);
    ApplicationDataPayload assessedData =
        reconciliationData(threshold.minus(20, ChronoUnit.MINUTES), false);
    ApplicationDataPayload recentData = reconciliationData(threshold.plusSeconds(1), null);
    when(applicationDataStore.getAll(any()))
        .thenReturn(
            Map.of(
                dataId(stalled), stalledData,
                dataId(assessed), assessedData,
                dataId(recent), recentData));

    StalledAssessments result = projection.handle(new FindStalledAssessmentsQuery(threshold));

    assertThat(result.applications())
        .containsExactly(
            new StalledAssessment(stalledId, 3L, threshold.minus(15, ChronoUnit.MINUTES)));
  }

  @Test
  void givenLinkedEvent_whenHandled_thenUpdatesLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationLinkedEvent event =
        new ApplicationLinkedEvent(applicationId, leadApplicationId, Instant.now());

    projection.on(event);

    assertThat(existing.getLeadApplicationId()).isEqualTo(leadApplicationId);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void givenDecisionEvent_whenHandled_thenAdvancesVersionsAndEmitsUpdate() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, "GRANTED", AutoGrantedState.MANUAL, occurredAt),
        queryUpdateEmitter);

    assertThat(existing.getApplicationVersion()).isEqualTo(3L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(4L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(queryUpdateEmitter)
        .emit(any(Class.class), any(Predicate.class), any(ApplicationReadModel.class));
  }

  @Test
  void givenReadyEvent_whenHandled_thenAdvancesReferencedDataVersion() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-21T09:30:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, occurredAt),
        queryUpdateEmitter);

    assertThat(existing.getStatus()).isNull();
    assertThat(existing.getApplicationVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
  }

  @Test
  void
      givenApplicationWorkItemAssigned_whenHandled_thenSetsCaseworkerWithoutChangingContentVersions() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationVersion(1L)
            .applicationDataVersion(1L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new WorkItemAssigned(
            applicationId,
            WorkItemType.APPLICATION,
            1L,
            1L,
            1L,
            caseworkerId,
            "Assigned",
            occurredAt));

    assertThat(existing.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(existing.getApplicationVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void
      givenApplicationWorkItemUnassigned_whenHandled_thenClearsCaseworkerWithoutChangingContentVersions() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .caseworkerId(UUID.randomUUID())
            .applicationVersion(1L)
            .applicationDataVersion(1L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new WorkItemUnassigned(
            applicationId, WorkItemType.APPLICATION, 1L, 1L, 2L, "Returned to queue", occurredAt));

    assertThat(existing.getCaseworkerId()).isNull();
    assertThat(existing.getApplicationVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void
      givenNoteCreatedEvent_whenHandled_thenAdvancesDataVersionWithoutChangingApplicationVersion() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationVersion(0L)
            .applicationDataVersion(0L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent(
            applicationId, 1L, occurredAt));

    assertThat(existing.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(existing.getApplicationVersion()).isEqualTo(0L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(existing);
  }

  @Test
  void givenExistingApplication_whenFindNotesQuery_thenReturnsNotesFromPayload() {
    UUID applicationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationNote note = new ApplicationNote("Test note", createdAt);
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(1L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId))
            .withNote("Test note", createdAt);
    when(applicationDataStore.get(applicationId, 1L)).thenReturn(payload);

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.notes()).containsExactly(note);
  }

  @Test
  void givenMissingApplication_whenFindNotesQuery_thenReturnsEmpty() {
    UUID applicationId = UUID.randomUUID();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId));

    assertThat(result).isNull();
  }

  @Test
  void givenApplicationWithNoNotes_whenFindNotesQuery_thenReturnsEmptyNotesList() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(0L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, 0L)).thenReturn(payload);

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.notes()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenIndexReturnsPage_whenFindAllApplicationsQuery_thenBatchLoadsBothSourcesAndAssembles() {
    UUID appId = UUID.randomUUID();
    ApplicationListIndexReadModel indexRow =
        ApplicationListIndexReadModel.builder().applicationId(appId).build();

    when(listIndexRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadRepository.findAllById(List.of(appId))).thenReturn(List.of(state));

    ApplicationDataId dataId = new ApplicationDataId(appId, 0L);
    ApplicationDataPayload payload = ApplicationDataPayload.from(applicationCreationDetails(appId));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, payload));

    when(groupReadRepository.findAllByLeadApplicationIdIn(any())).thenReturn(List.of());

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).hasSize(1);
    assertThat(result.applications().getFirst().getApplicationId()).isEqualTo(appId);
    assertThat(result.totalElements()).isEqualTo(1L);

    // Verify batch loads — not per-row findById calls
    verify(applicationReadRepository).findAllById(List.of(appId));
    verify(applicationDataStore).getAll(List.of(dataId));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenEmptyIndexPage_whenFindAllApplicationsQuery_thenReturnsEmptyResult() {
    when(listIndexRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(groupReadRepository.findAllByLeadApplicationIdIn(any())).thenReturn(List.of());

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).isEmpty();
    assertThat(result.totalElements()).isZero();
  }

  private ApplicationReadModel reconciliationReadModel(UUID applicationId) {
    return ApplicationReadModel.builder()
        .applicationId(applicationId)
        .status("APPLICATION_SUBMITTED")
        .applicationDataVersion(2L)
        .applicationVersion(3L)
        .build();
  }

  private ApplicationDataId dataId(ApplicationReadModel application) {
    return new ApplicationDataId(
        application.getApplicationId(), application.getApplicationDataVersion());
  }

  private ApplicationDataPayload reconciliationData(Instant submittedAt, Boolean autoGranted) {
    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
    when(data.submittedAt()).thenReturn(submittedAt);
    when(data.autoGranted()).thenReturn(AutoGrantedState.fromDecisionFlag(autoGranted));
    return data;
  }

  @Test
  void givenRefusedDecisionEvent_whenHandled_thenSetsApplicationRefusedStatus() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, "REFUSED", AutoGrantedState.MANUAL, occurredAt),
        queryUpdateEmitter);

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_REFUSED");
  }

  @Test
  void givenApplicationWithNullSubmittedAt_whenStalledAssessmentQuery_thenExcluded() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID appId = UUID.randomUUID();
    ApplicationReadModel app = reconciliationReadModel(appId);
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(app));
    // PENDING auto-grant but null submittedAt — must be excluded by the submittedAt != null filter
    ApplicationDataPayload data = reconciliationData(null, null);
    when(applicationDataStore.getAll(any())).thenReturn(Map.of(dataId(app), data));

    StalledAssessments result = projection.handle(new FindStalledAssessmentsQuery(threshold));

    assertThat(result.applications()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenLastUpdatedSortAndDescOrder_whenFindAllQuery_thenPagedWithCorrectSort() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(listIndexRepository.findAll(any(Specification.class), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(groupReadRepository.findAllByLeadApplicationIdIn(any())).thenReturn(List.of());

    projection.handle(
        new FindAllApplicationsQuery(
            null, null, null, null, null, null, "LAST_UPDATED_DATE", "DESC", 1, 20));

    Sort sort = pageableCaptor.getValue().getSort();
    assertThat(sort.getOrderFor("modifiedAt")).isNotNull();
    assertThat(sort.getOrderFor("modifiedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenApplicationWithLeadId_whenFindAllQuery_thenGroupFetchedByLeadId() {
    UUID appId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    ApplicationListIndexReadModel indexRow =
        ApplicationListIndexReadModel.builder().applicationId(appId).build();
    when(listIndexRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .leadApplicationId(leadId)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadRepository.findAllById(List.of(appId))).thenReturn(List.of(state));

    ApplicationDataId dataId = new ApplicationDataId(appId, 0L);
    ApplicationDataPayload payload = ApplicationDataPayload.from(applicationCreationDetails(appId));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, payload));

    ArgumentCaptor<List<UUID>> leadIdsCaptor = ArgumentCaptor.forClass(List.class);
    when(groupReadRepository.findAllByLeadApplicationIdIn(leadIdsCaptor.capture()))
        .thenReturn(List.of());

    projection.handle(
        new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(leadIdsCaptor.getValue()).containsExactly(leadId);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenDataMissingForStateRow_whenFindAllQuery_thenExcludesFromResult() {
    UUID appId = UUID.randomUUID();
    ApplicationListIndexReadModel indexRow =
        ApplicationListIndexReadModel.builder().applicationId(appId).build();
    when(listIndexRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadRepository.findAllById(List.of(appId))).thenReturn(List.of(state));
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());
    when(groupReadRepository.findAllByLeadApplicationIdIn(any())).thenReturn(List.of());

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).isEmpty();
  }

  @Test
  void givenDataMissingForApplication_whenFindByIdQuery_thenReturnsNull() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(0L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());

    ApplicationReadModel result = projection.handle(new FindApplicationByIdQuery(applicationId));

    assertThat(result).isNull();
  }

  @Test
  void givenExistingApplicationWithData_whenFindByIdQuery_thenReturnsHydratedModel() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(1L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, payload));

    ApplicationReadModel result = projection.handle(new FindApplicationByIdQuery(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.getApplicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenApplicationWithMissingData_whenStalledQuery_thenExcluded() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID appId = UUID.randomUUID();
    ApplicationReadModel app = reconciliationReadModel(appId);
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(app));
    // getAll returns empty map — data not found for this application
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());

    StalledAssessments result = projection.handle(new FindStalledAssessmentsQuery(threshold));

    assertThat(result.applications()).isEmpty();
  }

  @Test
  void givenNullDataFromStore_whenFindNotesQuery_thenReturnsEmptyNotes() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel existing =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(0L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationDataStore.get(applicationId, 0L)).thenReturn(null);

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.notes()).isEmpty();
  }
}
