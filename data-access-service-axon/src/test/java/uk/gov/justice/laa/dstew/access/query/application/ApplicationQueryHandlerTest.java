package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryHandlerTest {

  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private ApplicationSummaryReadRepository summaryRepository;
  @Mock private PriorAuthorityReadRepository priorAuthorityReadRepository;
  @Mock private ApplicationReadQueryGateway applicationReadQueryGateway;
  @Mock private ApplicationSummaryAccessPolicy summaryAccessPolicy;
  private ApplicationQueryHandler projection;

  @BeforeEach
  void setUp() {
    projection =
        new ApplicationQueryHandler(
            applicationReadQueryGateway,
            summaryAccessPolicy,
            summaryRepository,
            new ApplicationReadModelAssembler(
                groupReadRepository,
                ids ->
                    summaryRepository.findAllById(ids).stream()
                        .collect(
                            Collectors.toMap(
                                ApplicationSummaryReadModel::getApplicationId,
                                Function.identity())),
                priorAuthorityReadRepository));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenUploadedDocument_whenFound_thenReturnsFilenameFromSubmittedOrDraftContent(
      boolean submitted) {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T15:10:27.430Z");
    ApplicationAggregate aggregate =
        documentAggregate(
            applicationId, submitted, documentUpload(applicationId, documentId, uploadedAt));

    assertThat(
            projection.handle(
                new FindApplicationDocumentQuery(applicationId, documentId), aggregate))
        .isEqualTo(
            new EvidenceDocument(
                documentId,
                "GATEWAY_EVIDENCE",
                "original evidence.pdf",
                null,
                "application/pdf",
                12L,
                uploadedAt,
                "CIVIL_APPLY",
                "checksum",
                ".pdf"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing application", "unknown document"})
  void givenDocumentUnavailable_whenQueried_thenReturnsNull(String scenario) {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationAggregate aggregate =
        "missing application".equals(scenario)
            ? null
            : documentAggregate(
                applicationId,
                true,
                documentUpload(applicationId, UUID.randomUUID(), Instant.now()));

    assertThat(
            projection.handle(
                new FindApplicationDocumentQuery(applicationId, documentId), aggregate))
        .isNull();
  }

  @Test
  void givenDocumentWithErasedFilename_whenQueried_thenReturnsMetadata() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationDocumentUploadedEvent upload =
        new ApplicationDocumentUploadedEvent(
            applicationId,
            documentId,
            "GATEWAY_EVIDENCE",
            Instant.now(),
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY");
    ApplicationAggregate aggregate = documentAggregate(applicationId, false, upload);

    EvidenceDocument document =
        projection.handle(new FindApplicationDocumentQuery(applicationId, documentId), aggregate);

    assertThat(document).isNotNull();
    assertThat(document.documentId()).isEqualTo(documentId);
    assertThat(document.fileName()).isNull();
  }

  private ApplicationDocumentUploadedEvent documentUpload(
      UUID applicationId, UUID documentId, Instant uploadedAt) {
    return new ApplicationDocumentUploadedEvent(
        applicationId,
        documentId,
        "GATEWAY_EVIDENCE",
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        "CIVIL_APPLY",
        null,
        ".pdf",
        "original evidence.pdf");
  }

  private ApplicationAggregate documentAggregate(
      UUID applicationId, boolean submitted, ApplicationDocumentUploadedEvent upload) {
    List<Object> events = new ArrayList<>();
    events.add(draftStarted(applicationId, Instant.now(), "1A001B"));
    events.add(upload);
    if (submitted) {
      events.add(applicationCreatedEvent(applicationId));
    }
    return ApplicationStateEvents.aggregateOf(events);
  }

  private ApplicationAggregate submittedAggregate(UUID applicationId, Object... laterEvents) {
    List<Object> events = new ArrayList<>();
    events.add(applicationCreatedEvent(applicationId));
    events.addAll(List.of(laterEvents));
    return ApplicationStateEvents.aggregateOf(events);
  }

  private ApplicationDraftStartedEvent draftStarted(
      UUID applicationId, Instant occurredAt, String officeCode) {
    return new ApplicationDraftStartedEvent(
        applicationId,
        3,
        "fingerprint",
        occurredAt,
        officeCode,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of(),
        "{}",
        List.of());
  }

  @Test
  void givenSubmittedDraftDocument_whenDetailQueried_thenReturnsFilenameFromContent() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationAggregate aggregate =
        documentAggregate(
            applicationId, true, documentUpload(applicationId, documentId, Instant.now()));

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId), aggregate);

    assertThat(result.application().getDocumentFilenames())
        .containsEntry(documentId, "original evidence.pdf");
  }

  @Test
  void givenNoteCreated_whenFindNotesQuery_thenReturnsNotesFromAggregate() {
    UUID applicationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationAggregate aggregate =
        submittedAggregate(
            applicationId,
            new uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent(
                applicationId, 1L, createdAt, "Test note"));

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId), aggregate);

    assertThat(result).isNotNull();
    assertThat(result.notes()).containsExactly(new ApplicationNote("Test note", createdAt));
  }

  @Test
  void givenMissingApplication_whenFindNotesQuery_thenReturnsNull() {
    assertThat(projection.handle(new FindNotesForApplicationQuery(UUID.randomUUID()), null))
        .isNull();
  }

  @Test
  void givenApplicationWithNoNotes_whenFindNotesQuery_thenReturnsEmptyNotesList() {
    UUID applicationId = UUID.randomUUID();

    ApplicationNotesResult result =
        projection.handle(
            new FindNotesForApplicationQuery(applicationId), submittedAggregate(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.notes()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenIndexReturnsPage_whenFindAllApplicationsQuery_thenReturnsSummaryRowsWithRelatedData() {
    UUID appId = UUID.randomUUID();
    ApplicationSummaryReadModel indexRow =
        ApplicationSummaryReadModel.builder().applicationId(appId).laaReference("LAA-123").build();

    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(indexRow)));
    when(groupReadRepository.findAllById(any())).thenReturn(List.of());

    PriorAuthorityReadModel priorAuthority =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(appId)
            .status("DRAFT")
            .createdAt(Instant.parse("2026-09-11T10:00:00Z"))
            .build();
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(appId)))
        .thenReturn(List.of(priorAuthority));

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).containsExactly(indexRow);
    assertThat(result.totalElements()).isEqualTo(1L);
    assertThat(result.priorAuthoritiesByApplicationId())
        .containsEntry(appId, List.of(priorAuthority));
    verify(priorAuthorityReadRepository).findAllByApplicationIdIn(List.of(appId));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenEmptyIndexPage_whenFindAllApplicationsQuery_thenReturnsEmptyResult() {
    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(groupReadRepository.findAllById(any())).thenReturn(List.of());
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of())).thenReturn(List.of());

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).isEmpty();
    assertThat(result.totalElements()).isZero();
    assertThat(result.priorAuthoritiesByApplicationId()).isEmpty();
    verify(priorAuthorityReadRepository).findAllByApplicationIdIn(List.of());
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenTwoApplicationsOnPage_whenFindAllApplicationsQuery_thenPriorAuthoritiesStayIsolated() {
    UUID applicationIdWithPriorAuthority = UUID.randomUUID();
    UUID applicationIdWithout = UUID.randomUUID();

    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    ApplicationSummaryReadModel.builder()
                        .applicationId(applicationIdWithPriorAuthority)
                        .build(),
                    ApplicationSummaryReadModel.builder()
                        .applicationId(applicationIdWithout)
                        .build())));

    List<UUID> pageIds = List.of(applicationIdWithPriorAuthority, applicationIdWithout);

    when(groupReadRepository.findAllById(any())).thenReturn(List.of());

    PriorAuthorityReadModel priorAuthority =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(applicationIdWithPriorAuthority)
            .status("DRAFT")
            .createdAt(Instant.parse("2026-09-11T10:00:00Z"))
            .build();
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(pageIds))
        .thenReturn(List.of(priorAuthority));

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(result.applications()).hasSize(2);
    assertThat(result.priorAuthoritiesByApplicationId())
        .containsExactly(entry(applicationIdWithPriorAuthority, List.of(priorAuthority)));
    assertThat(result.priorAuthoritiesByApplicationId()).doesNotContainKey(applicationIdWithout);

    // Both page IDs must reach the repository in a single batch call, not one call per application
    ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(priorAuthorityReadRepository).findAllByApplicationIdIn(captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(applicationIdWithPriorAuthority, applicationIdWithout);
  }

  @Test
  void givenStalledSummaries_whenStalledQuery_thenMapsIdVersionAndSubmittedAt() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID appId = UUID.randomUUID();
    Instant submittedAt = threshold.minus(1, ChronoUnit.HOURS);
    when(summaryRepository.findAllByStatusAndAutoGrantedAndSubmittedAtBefore(
            "APPLICATION_SUBMITTED", AutoGrantedState.PENDING, threshold))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(appId)
                    .streamVersion(3L)
                    .submittedAt(submittedAt)
                    .build()));

    StalledAssessments result = projection.handle(new FindStalledAssessmentsQuery(threshold));

    assertThat(result.applications())
        .containsExactly(new StalledAssessment(appId, 3L, submittedAt));
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenLastUpdatedSortAndDescOrder_whenFindAllQuery_thenPagedWithCorrectSort() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(applicationReadQueryGateway.findApplicationIndexPage(
            any(), pageableCaptor.capture(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(groupReadRepository.findAllById(any())).thenReturn(List.of());

    projection.handle(
        new FindAllApplicationsQuery(
            null, null, null, null, null, null, "LAST_UPDATED_DATE", "DESC", 1, 20));

    Sort sort = pageableCaptor.getValue().getSort();
    assertThat(sort.getOrderFor("modifiedAt")).isNotNull();
    assertThat(sort.getOrderFor("modifiedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenSubmittedDateSort_whenFindAllQuery_thenUsesSubmittedAtIndexColumn() {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(applicationReadQueryGateway.findApplicationIndexPage(
            any(), pageableCaptor.capture(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(groupReadRepository.findAllById(any())).thenReturn(List.of());

    projection.handle(
        new FindAllApplicationsQuery(
            null, null, null, null, null, null, "SUBMITTED_DATE", "ASC", 1, 20));

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("submittedAt")).isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenApplicationWithGroupId_whenFindAllQuery_thenFetchesGroupAndMemberDetails() {
    UUID appId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationSummaryReadModel indexRow =
        ApplicationSummaryReadModel.builder().applicationId(appId).linkedGroupId(groupId).build();
    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ArgumentCaptor<List<UUID>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(appId)
            .memberIds(List.of(appId))
            .build();
    when(groupReadRepository.findAllById(groupIdsCaptor.capture())).thenReturn(List.of(group));
    when(summaryRepository.findAllById(List.of(appId)))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(appId)
                    .laaReference("LAA-123")
                    .status("APPLICATION_SUBMITTED")
                    .clientFirstName("Ada")
                    .clientLastName("Lovelace")
                    .build()));

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(groupIdsCaptor.getValue()).containsExactly(groupId);
    assertThat(result.groupsByGroupId()).containsEntry(groupId, group);
    assertThat(result.linkedMemberDetails())
        .containsExactly(
            entry(
                appId,
                new LinkedApplicationMemberDetails(
                    "LAA-123", "APPLICATION_SUBMITTED", "Ada", "Lovelace")));
  }

  @Test
  void givenLeadApplication_whenFindingDetail_thenReturnsHydratedApplicationAndRelatedData() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .leadApplicationId(applicationId)
            .memberIds(List.of(applicationId, linkedApplicationId))
            .build();
    PriorAuthorityReadModel priorAuthority =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(applicationId)
            .status("DRAFT")
            .createdAt(Instant.parse("2026-09-01T10:00:00Z"))
            .build();
    stubSummary(applicationId, groupId, applicationId);
    group.setGroupId(groupId);
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(summaryRepository.findAllById(List.of(applicationId, linkedApplicationId)))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(applicationId)
                    .laaReference("LAA-APPLICATION")
                    .status("APPLICATION_SUBMITTED")
                    .clientFirstName("Ada")
                    .clientLastName("Lovelace")
                    .build(),
                ApplicationSummaryReadModel.builder()
                    .applicationId(linkedApplicationId)
                    .laaReference("LAA-LINKED")
                    .status("APPLICATION_SUBMITTED")
                    .clientFirstName("Grace")
                    .clientLastName("Hopper")
                    .build()));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of(priorAuthority));

    ApplicationDetailResult result =
        projection.handle(
            new FindApplicationDetailQuery(applicationId), submittedAggregate(applicationId));

    assertThat(result.application().getLaaReference()).isEqualTo("LAA-123");
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.priorAuthorities()).containsExactly(priorAuthority);
    org.mockito.Mockito.verifyNoInteractions(applicationReadQueryGateway);
  }

  @Test
  void givenMemberApplication_whenFindingDetail_thenReturnsLeadGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .leadApplicationId(leadApplicationId)
            .memberIds(List.of(leadApplicationId, applicationId))
            .build();
    stubSummary(applicationId, groupId, leadApplicationId);
    group.setGroupId(groupId);
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(summaryRepository.findAllById(List.of(leadApplicationId, applicationId)))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(leadApplicationId)
                    .laaReference("LAA-LEAD")
                    .status("APPLICATION_SUBMITTED")
                    .clientFirstName("Ada")
                    .clientLastName("Lovelace")
                    .build(),
                ApplicationSummaryReadModel.builder()
                    .applicationId(applicationId)
                    .laaReference("LAA-MEMBER")
                    .status("APPLICATION_SUBMITTED")
                    .clientFirstName("Grace")
                    .clientLastName("Hopper")
                    .build()));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());

    ApplicationDetailResult result =
        projection.handle(
            new FindApplicationDetailQuery(applicationId), submittedAggregate(applicationId));

    assertThat(result.application().getApplicationId()).isEqualTo(applicationId);
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.priorAuthorities()).isEmpty();
  }

  @Test
  void givenStandaloneApplication_whenFindingDetail_thenReturnsWithoutRelatedData() {
    UUID applicationId = UUID.randomUUID();
    stubSummary(applicationId, null, null);
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());

    ApplicationDetailResult result =
        projection.handle(
            new FindApplicationDetailQuery(applicationId), submittedAggregate(applicationId));

    assertThat(result.application().getApplicationId()).isEqualTo(applicationId);
    assertThat(result.linkedGroup()).isNull();
    assertThat(result.priorAuthorities()).isEmpty();
  }

  @Test
  void givenMissingApplication_whenFindingDetail_thenReturnsNull() {
    assertThat(projection.handle(new FindApplicationDetailQuery(UUID.randomUUID()), null)).isNull();
  }

  @Test
  void givenDraftOnly_whenFindingById_thenReturnsNull() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate aggregate =
        ApplicationStateEvents.aggregateOf(
            List.of(draftStarted(applicationId, Instant.now(), "1A001B")));

    assertThat(projection.handle(new FindApplicationByIdQuery(applicationId), aggregate)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1A001B", "OTHER"})
  void givenOfficeScope_whenFindingById_thenReturnsOnlyPermittedOffice(String permittedOffice) {
    UUID applicationId = UUID.randomUUID();

    ApplicationReadModel result =
        projection.handle(
            new FindApplicationByIdQuery(
                applicationId, new OfficeCodeReadAccessScope(Set.of(permittedOffice))),
            submittedAggregate(applicationId));

    assertThat(result != null).isEqualTo("1A001B".equals(permittedOffice));
  }

  @Test
  void givenErasedContent_whenFindingById_thenReturnsNull() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate aggregate =
        submittedAggregate(
            applicationId,
            new ApplicationUpdatedEvent(
                applicationId,
                1L,
                1L,
                "APPLICATION_SUBMITTED",
                "APPLICATION_SUBMITTED",
                Instant.now()));

    assertThat(projection.handle(new FindApplicationByIdQuery(applicationId), aggregate)).isNull();
  }

  @Test
  void givenSubmittedApplication_whenFindByIdQuery_thenBuildsModelFromAggregateAndSummary() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant groupJoinedAt = Instant.parse("2030-01-01T00:00:00Z");
    ApplicationCreatedEvent created = applicationCreatedEvent(applicationId);
    when(summaryRepository.findAllById(List.of(applicationId)))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(applicationId)
                    .linkedGroupId(groupId)
                    .leadApplicationId(leadId)
                    .modifiedAt(groupJoinedAt)
                    .build()));

    ApplicationReadModel result =
        projection.handle(
            new FindApplicationByIdQuery(applicationId),
            submittedAggregate(
                applicationId,
                new WorkItemAssigned(
                    applicationId,
                    WorkItemType.APPLICATION,
                    applicationId,
                    0L,
                    1L,
                    caseworkerId,
                    Instant.parse("2026-07-16T00:00:00Z"))));

    assertThat(result).isNotNull();
    assertThat(result.getLaaReference()).isEqualTo("LAA-123");
    assertThat(result.getOfficeCode()).isEqualTo("1A001B");
    assertThat(result.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(result.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(result.getCreatedAt()).isEqualTo(created.occurredAt());
    assertThat(result.getSubmittedAt()).isEqualTo(created.occurredAt());
    assertThat(result.getModifiedAt()).isEqualTo(groupJoinedAt);
    assertThat(result.getLinkedGroupId()).isEqualTo(groupId);
    assertThat(result.getLeadApplicationId()).isEqualTo(leadId);
    org.mockito.Mockito.verifyNoInteractions(applicationReadQueryGateway);
  }

  private void stubSummary(UUID applicationId, UUID groupId, UUID leadId) {
    when(summaryRepository.findAllById(List.of(applicationId)))
        .thenReturn(
            List.of(
                ApplicationSummaryReadModel.builder()
                    .applicationId(applicationId)
                    .linkedGroupId(groupId)
                    .leadApplicationId(leadId)
                    .build()));
  }
}
