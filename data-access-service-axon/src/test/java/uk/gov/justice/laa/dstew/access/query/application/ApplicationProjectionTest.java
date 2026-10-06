package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationProjectionTest {

  @Mock private ApplicationReadRepository applicationReadRepository;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private QueryUpdateEmitter queryUpdateEmitter;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private ApplicationListIndexReadRepository listIndexRepository;
  @Mock private PriorAuthorityReadRepository priorAuthorityReadRepository;
  @Mock private ApplicationDraftStore draftStore;
  @Mock private ApplicationReadQueryGateway applicationReadQueryGateway;
  @Mock private ApplicationCurrentStateAccessPolicy currentStateAccessPolicy;
  @Mock private ApplicationListIndexAccessPolicy listIndexAccessPolicy;
  private ApplicationProjection projection;

  @BeforeEach
  void setUp() {
    projection =
        new ApplicationProjection(
            applicationReadRepository,
            applicationDataStore,
            draftStore,
            applicationReadQueryGateway,
            currentStateAccessPolicy,
            listIndexAccessPolicy,
            new ApplicationReadModelAssembler(
                applicationDataStore,
                groupReadRepository,
                listIndexRepository,
                priorAuthorityReadRepository));
  }

  @Test
  void givenDocumentUpload_whenReplayedTwice_thenMetadataAndVersionArePersistedOnce() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T15:10:27.430Z");
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(0L)
            .applicationVersion(7L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));
    ApplicationDocumentUploadedEvent event =
        new ApplicationDocumentUploadedEvent(
            applicationId,
            documentId,
            "GATEWAY_EVIDENCE",
            uploadedAt,
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY",
            1L);

    projection.on(event);
    projection.on(event);

    verify(applicationReadRepository).save(application);
    assertThat(application.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(application.getApplicationVersion()).isEqualTo(7L);
    assertThat(application.getModifiedAt()).isEqualTo(uploadedAt);
    assertThat(application.getUploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.documentId()).isEqualTo(documentId);
              assertThat(document.deleted()).isFalse();
            });
    assertThat(application.getDocumentFilenames()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenProjectionExistence_whenQueried_thenReturnsExistenceWithoutHydration(boolean exists) {
    UUID applicationId = UUID.randomUUID();
    when(applicationReadRepository.existsById(applicationId)).thenReturn(exists);

    assertThat(projection.handle(new ApplicationProjectionExistsQuery(applicationId)))
        .isEqualTo(exists);
    org.mockito.Mockito.verifyNoInteractions(applicationDataStore);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenProjectedDocument_whenFound_thenHydratesFilenameFromSubmittedOrDraftContent(
      boolean submitted) {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T15:10:27.430Z");
    ApplicationReadModel application =
        documentReadModel(applicationId, uploadedDocument(documentId, uploadedAt, false));
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));
    givenDocumentFilenames(application, submitted, Map.of(documentId, "original evidence.pdf"));

    assertThat(projection.handle(new FindApplicationDocumentQuery(applicationId, documentId)))
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
                "checksum"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing application", "unknown document", "deleted", "no filename"})
  void givenDocumentUnavailable_whenQueried_thenReturnsNull(String scenario) {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationReadModel application =
        documentReadModel(
            applicationId,
            uploadedDocument(
                "unknown document".equals(scenario) ? UUID.randomUUID() : documentId,
                Instant.parse("2026-09-28T15:10:27.430Z"),
                "deleted".equals(scenario)));
    when(applicationReadRepository.findById(applicationId))
        .thenReturn(
            "missing application".equals(scenario) ? Optional.empty() : Optional.of(application));
    if ("no filename".equals(scenario)) {
      givenDocumentFilenames(application, false, Map.of());
    }

    assertThat(projection.handle(new FindApplicationDocumentQuery(applicationId, documentId)))
        .isNull();
  }

  private ApplicationReadModel documentReadModel(UUID applicationId, UploadDocument document) {
    return ApplicationReadModel.builder()
        .applicationId(applicationId)
        .applicationDataVersion(0L)
        .uploadedDocuments(List.of(document))
        .build();
  }

  private UploadDocument uploadedDocument(UUID documentId, Instant uploadedAt, boolean deleted) {
    return new UploadDocument(
        documentId,
        "GATEWAY_EVIDENCE",
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        "CIVIL_APPLY",
        deleted);
  }

  private void givenDocumentFilenames(
      ApplicationReadModel application, boolean submitted, Map<UUID, String> filenames) {
    ApplicationDataId id = dataId(application);
    if (submitted) {
      ApplicationDataPayload payload = mock(ApplicationDataPayload.class);
      when(payload.documentFilenames()).thenReturn(filenames);
      when(applicationDataStore.getAll(List.of(id))).thenReturn(Map.of(id, payload));
    } else {
      when(applicationDataStore.getAll(List.of(id))).thenReturn(Map.of());
      when(draftStore.find(application.getApplicationId()))
          .thenReturn(
              Optional.of(
                  new ApplicationDraftPayload(
                      "APPLICATION_SUBMITTED", "LAA-123", Map.of(), "{}", List.of(), filenames)));
    }
  }

  @Test
  void givenDraftStarted_whenProjected_thenCreatesReadableDraftAndEmitsUpdate() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-05T10:00:00Z");
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationReadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(
        new ApplicationDraftStartedEvent(applicationId, 3, "fingerprint", occurredAt),
        queryUpdateEmitter);

    ArgumentCaptor<ApplicationReadModel> saved =
        ArgumentCaptor.forClass(ApplicationReadModel.class);
    verify(applicationReadRepository).save(saved.capture());
    assertThat(saved.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getValue().getStatus())
        .isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS.getValue());
    assertThat(saved.getValue().getSchemaVersion()).isEqualTo(3);
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(occurredAt);
    assertThat(saved.getValue().getModifiedAt()).isEqualTo(occurredAt);
    verify(queryUpdateEmitter)
        .emit(
            ArgumentMatchers.eq(ApplicationProjectionExistsQuery.class),
            ArgumentMatchers.<Predicate<ApplicationProjectionExistsQuery>>any(),
            ArgumentMatchers.eq(true));
  }

  @Test
  void givenDraftDocument_thenApplicationCreated_thenPreservesProjectedMetadata() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationReadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(
        new ApplicationDraftStartedEvent(applicationId, 1, "fingerprint", Instant.now()),
        queryUpdateEmitter);
    ArgumentCaptor<ApplicationReadModel> draft =
        ArgumentCaptor.forClass(ApplicationReadModel.class);
    verify(applicationReadRepository).save(draft.capture());
    when(applicationReadRepository.findById(applicationId))
        .thenReturn(Optional.of(draft.getValue()));

    projection.on(
        new ApplicationDocumentUploadedEvent(
            applicationId,
            documentId,
            "GATEWAY_EVIDENCE",
            Instant.now(),
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY"));

    projection.on(applicationCreatedEvent(applicationId), queryUpdateEmitter);

    ArgumentCaptor<ApplicationReadModel> created =
        ArgumentCaptor.forClass(ApplicationReadModel.class);
    verify(applicationReadRepository, org.mockito.Mockito.times(3)).save(created.capture());
    assertThat(created.getValue().getUploadedDocuments())
        .singleElement()
        .satisfies(document -> assertThat(document.documentId()).isEqualTo(documentId));
    assertThat(created.getValue().getApplicationDataVersion()).isZero();
    assertThat(created.getValue().getSchemaVersion()).isEqualTo(1);
  }

  @Test
  void givenLegacyDocumentUpload_whenProjected_thenRetainsDataVersionAndUnknownFilename() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(4L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));

    projection.on(
        new ApplicationDocumentUploadedEvent(
            applicationId,
            UUID.randomUUID(),
            "GATEWAY_EVIDENCE",
            Instant.now(),
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY"));

    assertThat(application.getApplicationDataVersion()).isEqualTo(4L);
    assertThat(application.getUploadedDocuments()).hasSize(1);
  }

  @Test
  void givenDocumentFilenamePayload_whenDetailQueried_thenHydratesReferencedVersion() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(2L)
            .build();
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 2L);
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(List.of(dataId)))
        .thenReturn(
            Map.of(
                dataId,
                ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                    .withDocumentFilename(documentId, "client-report.pdf")));

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId));

    assertThat(result.application().getDocumentFilenames())
        .containsEntry(documentId, "client-report.pdf");
    verify(applicationDataStore).getAll(List.of(dataId));
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
  void givenCreatedEvent_whenHandled_thenInitializesApplicationWithoutLinkedGroup() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel[] saved = new ApplicationReadModel[1];
    when(applicationReadRepository.save(any()))
        .thenAnswer(
            invocation -> {
              saved[0] = invocation.getArgument(0);
              return saved[0];
            });

    projection.on(applicationCreatedEvent(applicationId), queryUpdateEmitter);

    assertThat(saved[0].getLinkedGroupId()).isNull();
    assertThat(saved[0].getLeadApplicationId()).isNull();
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllProjections() {
    projection.reset();

    verify(applicationReadRepository).deleteAllInBatch();
  }

  @Test
  void
      givenCreatedEventWithPotentialDuplicates_whenHandled_thenSavesPotentialDuplicatesToReadModel() {
    UUID applicationId = UUID.randomUUID();
    List<PotentialDuplicate> duplicates =
        List.of(
            new PotentialDuplicate("LAA-456").applicationId(UUID.randomUUID()),
            new PotentialDuplicate("LAA-789").legacyReference("LEGACY-001"));
    ApplicationCreatedEvent eventWithDuplicates =
        new ApplicationCreatedEvent(
            applicationId,
            0L,
            ApplicationDataStore.fingerprint("{}"),
            "APPLICATION_SUBMITTED",
            1,
            Instant.parse("2026-07-15T08:00:00Z"),
            duplicates);
    ApplicationReadModel[] savedCapture = new ApplicationReadModel[1];
    when(applicationReadRepository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(eventWithDuplicates, queryUpdateEmitter);

    assertThat(savedCapture[0].getPotentialDuplicates()).isEqualTo(duplicates);
  }

  @Test
  void
      givenCreatedEventWithNoPotentialDuplicates_whenHandled_thenSavesNormalizedEmptyListToReadModel() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationReadModel[] savedCapture = new ApplicationReadModel[1];
    when(applicationReadRepository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(event, queryUpdateEmitter);

    // ApplicationCreationDetails normalizes null to empty list, so projection saves empty list
    assertThat(savedCapture[0].getPotentialDuplicates()).isEmpty();
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
  void givenGroupCreatedEvent_whenHandled_thenKeepsLeadUnlinkedAndLinksMembers() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    ApplicationReadModel lead =
        ApplicationReadModel.builder().applicationId(leadApplicationId).build();
    ApplicationReadModel member =
        ApplicationReadModel.builder().applicationId(memberApplicationId).build();
    when(applicationReadRepository.findById(leadApplicationId)).thenReturn(Optional.of(lead));
    when(applicationReadRepository.findById(memberApplicationId)).thenReturn(Optional.of(member));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId,
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId),
            occurredAt));

    assertThat(lead.getLeadApplicationId()).isNull();
    assertThat(lead.getLinkedGroupId()).isEqualTo(groupId);
    assertThat(lead.getModifiedAt()).isEqualTo(occurredAt);
    assertThat(member.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(member.getLinkedGroupId()).isEqualTo(groupId);
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(lead);
    verify(applicationReadRepository).save(member);
  }

  @Test
  void givenMemberAddedToGroupEvent_whenHandled_thenLinksAddedMemberToLead() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T09:00:00Z");
    ApplicationReadModel member =
        ApplicationReadModel.builder().applicationId(memberApplicationId).build();
    when(applicationReadRepository.findById(memberApplicationId)).thenReturn(Optional.of(member));

    projection.on(
        new MemberAddedToGroupEvent(groupId, leadApplicationId, memberApplicationId, occurredAt));

    assertThat(member.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(member.getLinkedGroupId()).isEqualTo(groupId);
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(member);
  }

  @Test
  void givenLeadChangedEvent_whenHandled_thenUpdatesLeadForEveryGroupMember() {
    UUID groupId = UUID.randomUUID();
    UUID previousLeadId = UUID.randomUUID();
    UUID newLeadId = UUID.randomUUID();
    UUID otherMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T10:00:00Z");
    ApplicationReadModel previousLead =
        ApplicationReadModel.builder().applicationId(previousLeadId).linkedGroupId(groupId).build();
    ApplicationReadModel newLead =
        ApplicationReadModel.builder().applicationId(newLeadId).linkedGroupId(groupId).build();
    ApplicationReadModel otherMember =
        ApplicationReadModel.builder().applicationId(otherMemberId).linkedGroupId(groupId).build();
    when(applicationReadRepository.findAllByLinkedGroupId(groupId))
        .thenReturn(List.of(previousLead, newLead, otherMember));

    projection.on(
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, previousLeadId, newLeadId, 1L, occurredAt));

    assertThat(previousLead.getLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(newLead.getLeadApplicationId()).isNull();
    assertThat(otherMember.getLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(List.of(previousLead, newLead, otherMember))
        .allSatisfy(application -> assertThat(application.getModifiedAt()).isEqualTo(occurredAt));
    verify(applicationReadRepository).saveAll(List.of(previousLead, newLead, otherMember));
  }

  @Test
  void givenMemberRemovedEvent_whenHandled_thenClearsGroupMembership() {
    UUID groupId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    ApplicationReadModel member =
        ApplicationReadModel.builder()
            .applicationId(memberId)
            .linkedGroupId(groupId)
            .leadApplicationId(UUID.randomUUID())
            .build();
    when(applicationReadRepository.findById(memberId)).thenReturn(Optional.of(member));
    Instant occurredAt = Instant.parse("2026-07-15T11:00:00Z");

    projection.on(
        new MemberRemovedFromGroupEvent(groupId, UUID.randomUUID(), memberId, 2L, occurredAt));

    assertThat(member.getLinkedGroupId()).isNull();
    assertThat(member.getLeadApplicationId()).isNull();
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(applicationReadRepository).save(member);
  }

  @Test
  void givenDissolvedEvent_whenHandled_thenClearsMembershipForEveryApplication() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    ApplicationReadModel lead =
        ApplicationReadModel.builder().applicationId(leadId).linkedGroupId(groupId).build();
    ApplicationReadModel removed =
        ApplicationReadModel.builder().applicationId(removedId).linkedGroupId(groupId).build();
    when(applicationReadRepository.findById(leadId)).thenReturn(Optional.of(lead));
    when(applicationReadRepository.findById(removedId)).thenReturn(Optional.of(removed));
    Instant occurredAt = Instant.parse("2026-07-15T11:00:00Z");

    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            groupId, leadId, removedId, List.of(leadId, removedId), 2L, occurredAt));

    assertThat(lead.getLinkedGroupId()).isNull();
    assertThat(lead.getLeadApplicationId()).isNull();
    assertThat(removed.getLinkedGroupId()).isNull();
    assertThat(removed.getLeadApplicationId()).isNull();
    verify(applicationReadRepository).save(lead);
    verify(applicationReadRepository).save(removed);
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
            applicationId, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, occurredAt));

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
        new WorkItemUnassigned(applicationId, WorkItemType.APPLICATION, 1L, 2L, occurredAt));

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
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(existing));
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
    when(applicationReadQueryGateway.findApplication(any(), any())).thenReturn(Optional.empty());

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
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(existing));
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

    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadQueryGateway.findApplications(eq(List.of(appId)), any()))
        .thenReturn(List.of(state));

    ApplicationDataId dataId = new ApplicationDataId(appId, 0L);
    ApplicationDataPayload payload = ApplicationDataPayload.from(applicationCreationDetails(appId));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, payload));

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

    assertThat(result.applications()).hasSize(1);
    assertThat(result.applications().getFirst().getApplicationId()).isEqualTo(appId);
    assertThat(result.totalElements()).isEqualTo(1L);
    assertThat(result.priorAuthoritiesByApplicationId())
        .containsEntry(appId, List.of(priorAuthority));

    // Verify batch loads — not per-row findById calls
    verify(applicationReadQueryGateway).findApplications(eq(List.of(appId)), any());
    verify(applicationDataStore).getAll(List.of(dataId));
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
                    ApplicationListIndexReadModel.builder()
                        .applicationId(applicationIdWithPriorAuthority)
                        .build(),
                    ApplicationListIndexReadModel.builder()
                        .applicationId(applicationIdWithout)
                        .build())));

    List<UUID> pageIds = List.of(applicationIdWithPriorAuthority, applicationIdWithout);
    when(applicationReadQueryGateway.findApplications(eq(pageIds), any()))
        .thenReturn(pageIds.stream().map(ApplicationProjectionTest::pageState).toList());

    when(applicationDataStore.getAll(any()))
        .thenReturn(
            pageIds.stream()
                .collect(
                    Collectors.toMap(
                        id -> new ApplicationDataId(id, 0L),
                        id -> ApplicationDataPayload.from(applicationCreationDetails(id)))));

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

  private static ApplicationReadModel pageState(UUID applicationId) {
    return ApplicationReadModel.builder()
        .applicationId(applicationId)
        .applicationDataVersion(0L)
        .modifiedAt(Instant.EPOCH)
        .build();
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
  void givenApplicationWithGroupId_whenFindAllQuery_thenGroupFetchedByGroupId() {
    UUID appId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationListIndexReadModel indexRow =
        ApplicationListIndexReadModel.builder().applicationId(appId).build();
    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .linkedGroupId(groupId)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadQueryGateway.findApplications(eq(List.of(appId)), any()))
        .thenReturn(List.of(state));

    ApplicationDataId dataId = new ApplicationDataId(appId, 0L);
    ApplicationDataPayload payload = ApplicationDataPayload.from(applicationCreationDetails(appId));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, payload));

    ArgumentCaptor<List<UUID>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(appId)
            .memberIds(List.of(appId))
            .build();
    when(groupReadRepository.findAllById(groupIdsCaptor.capture())).thenReturn(List.of(group));

    FindAllApplicationsResult result =
        projection.handle(
            new FindAllApplicationsQuery(null, null, null, null, null, null, null, null, 1, 20));

    assertThat(groupIdsCaptor.getValue()).containsExactly(groupId);
    assertThat(result.groupsByGroupId()).containsEntry(groupId, group);
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenDataMissingForStateRow_whenFindAllQuery_thenExcludesFromResult() {
    UUID appId = UUID.randomUUID();
    ApplicationListIndexReadModel indexRow =
        ApplicationListIndexReadModel.builder().applicationId(appId).build();
    when(applicationReadQueryGateway.findApplicationIndexPage(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(indexRow)));

    ApplicationReadModel state =
        ApplicationReadModel.builder()
            .applicationId(appId)
            .applicationDataVersion(0L)
            .modifiedAt(Instant.EPOCH)
            .build();
    when(applicationReadQueryGateway.findApplications(eq(List.of(appId)), any()))
        .thenReturn(List.of(state));
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());
    when(groupReadRepository.findAllById(any())).thenReturn(List.of());

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
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(existing));
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());

    ApplicationReadModel result = projection.handle(new FindApplicationByIdQuery(applicationId));

    assertThat(result).isNull();
  }

  @Test
  void givenLeadApplication_whenFindingDetail_thenReturnsHydratedApplicationAndRelatedData() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .linkedGroupId(UUID.randomUUID())
            .applicationDataVersion(1L)
            .build();
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload applicationData =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .leadApplicationId(applicationId)
            .memberIds(List.of(applicationId, UUID.randomUUID()))
            .build();
    PriorAuthorityReadModel priorAuthority =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(applicationId)
            .status("DRAFT")
            .createdAt(Instant.parse("2026-09-01T10:00:00Z"))
            .build();
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, applicationData));
    UUID groupId = application.getLinkedGroupId();
    group.setGroupId(groupId);
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(group));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of(priorAuthority));

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId));

    assertThat(result.application().getLaaReference()).isEqualTo("LAA-123");
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.priorAuthorities()).containsExactly(priorAuthority);
    verify(applicationReadQueryGateway).findApplication(any(), any());
  }

  @Test
  void givenMemberApplication_whenFindingDetail_thenReturnsLeadGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(1L)
            .linkedGroupId(UUID.randomUUID())
            .build();
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload applicationData =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .leadApplicationId(leadApplicationId)
            .memberIds(List.of(leadApplicationId, applicationId))
            .build();
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, applicationData));
    group.setGroupId(application.getLinkedGroupId());
    when(groupReadRepository.findById(application.getLinkedGroupId()))
        .thenReturn(Optional.of(group));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId));

    assertThat(result.application().getApplicationId()).isEqualTo(applicationId);
    assertThat(result.linkedGroup()).isSameAs(group);
    assertThat(result.priorAuthorities()).isEmpty();
  }

  @Test
  void givenStandaloneApplication_whenFindingDetail_thenReturnsWithoutRelatedData() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(1L)
            .build();
    ApplicationDataId dataId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload applicationData =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(application));
    when(applicationDataStore.getAll(List.of(dataId))).thenReturn(Map.of(dataId, applicationData));
    when(priorAuthorityReadRepository.findAllByApplicationIdIn(List.of(applicationId)))
        .thenReturn(List.of());

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId));

    assertThat(result.application().getApplicationId()).isEqualTo(applicationId);
    assertThat(result.linkedGroup()).isNull();
    assertThat(result.priorAuthorities()).isEmpty();
  }

  @Test
  void givenMissingApplication_whenFindingDetail_thenReturnsNull() {
    UUID applicationId = UUID.randomUUID();
    when(applicationReadQueryGateway.findApplication(any(), any())).thenReturn(Optional.empty());

    ApplicationDetailResult result =
        projection.handle(new FindApplicationDetailQuery(applicationId));

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
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(existing));
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
    when(applicationReadQueryGateway.findApplication(any(), any()))
        .thenReturn(Optional.of(existing));
    when(applicationDataStore.get(applicationId, 0L)).thenReturn(null);

    ApplicationNotesResult result =
        projection.handle(new FindNotesForApplicationQuery(applicationId));

    assertThat(result).isNotNull();
    assertThat(result.notes()).isEmpty();
  }
}
