package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

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
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
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
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;

@ExtendWith(MockitoExtension.class)
class ApplicationProjectionTest {

  @Mock private ApplicationReadRepository applicationReadRepository;
  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  @Mock private QueryUpdateEmitter queryUpdateEmitter;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private ApplicationSummaryReadRepository summaryRepository;
  @Mock private PriorAuthorityReadRepository priorAuthorityReadRepository;
  @Mock private ApplicationReadQueryGateway applicationReadQueryGateway;
  @Mock private ApplicationCurrentStateAccessPolicy currentStateAccessPolicy;
  @Mock private ApplicationSummaryAccessPolicy summaryAccessPolicy;
  private ApplicationProjection projection;

  @BeforeEach
  void setUp() {
    projection =
        new ApplicationProjection(
            applicationReadRepository,
            applicationDataStore,
            applicationReadQueryGateway,
            currentStateAccessPolicy,
            summaryAccessPolicy,
            new ApplicationReadModelAssembler(
                applicationDataStore,
                applicationReadRepository,
                groupReadRepository,
                ids ->
                    summaryRepository.findAllById(ids).stream()
                        .collect(
                            Collectors.toMap(
                                ApplicationSummaryReadModel::getApplicationId,
                                Function.identity())),
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
  void givenDraftStarted_whenProjected_thenCreatesDraftRow() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-05T10:00:00Z");
    String officeCode = "1A001B";
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationReadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(draftStarted(applicationId, occurredAt, officeCode));

    ArgumentCaptor<ApplicationReadModel> saved =
        ArgumentCaptor.forClass(ApplicationReadModel.class);
    verify(applicationReadRepository).save(saved.capture());
    assertThat(saved.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getValue().getStatus())
        .isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS.getValue());
    assertThat(saved.getValue().getSchemaVersion()).isEqualTo(3);
    assertThat(saved.getValue().getCreatedAt()).isEqualTo(occurredAt);
    assertThat(saved.getValue().getModifiedAt()).isEqualTo(occurredAt);
    assertThat(saved.getValue().getOfficeCode()).isEqualTo(officeCode);
  }

  @Test
  void givenDraftDocument_thenApplicationCreated_thenPreservesProjectedMetadata() {
    UUID applicationId = UUID.randomUUID();
    final UUID documentId = UUID.randomUUID();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationReadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    projection.on(draftStarted(applicationId, Instant.now(), "1A001B"));
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

    projection.on(applicationCreatedEvent(applicationId));

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
  void givenDraftThenCreate_whenProjected_thenRetainsDraftCreatedAtAndEventSubmittedAt() {
    UUID applicationId = UUID.randomUUID();
    Instant draftStartedAt = Instant.parse("2026-07-14T10:00:00Z");
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationReadModel[] draft = new ApplicationReadModel[1];
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.empty());
    when(applicationReadRepository.save(any()))
        .thenAnswer(
            invocation -> {
              draft[0] = invocation.getArgument(0);
              return draft[0];
            });
    ApplicationDataPayload data =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, event.applicationDataVersion())).thenReturn(data);

    projection.on(draftStarted(applicationId, draftStartedAt, null));
    ApplicationReadModel savedDraft = draft[0];
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(savedDraft));

    projection.on(event);

    assertThat(draft[0].getCreatedAt()).isEqualTo(draftStartedAt);
    assertThat(draft[0].getSubmittedAt()).isEqualTo(event.occurredAt());
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
    org.mockito.Mockito.verifyNoInteractions(applicationDataStore);
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

    projection.on(applicationCreatedEvent(applicationId));

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
            duplicates,
            null,
            "LAA-123");
    ApplicationReadModel[] savedCapture = new ApplicationReadModel[1];
    when(applicationReadRepository.save(any()))
        .thenAnswer(
            invocation -> {
              savedCapture[0] = invocation.getArgument(0);
              return savedCapture[0];
            });

    projection.on(eventWithDuplicates);

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

    projection.on(event);

    // ApplicationCreationDetails normalizes null to empty list, so projection saves empty list
    assertThat(savedCapture[0].getPotentialDuplicates()).isEmpty();
  }

  @Test
  void givenSubmittedApplications_whenReconciliationQueries_thenReturnsOnlyOldUnassessedOnes() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID stalledId = UUID.randomUUID();
    UUID assessedId = UUID.randomUUID();
    UUID recentId = UUID.randomUUID();
    Instant stalledSubmittedAt = threshold.minus(15, ChronoUnit.MINUTES);
    ApplicationReadModel stalled = reconciliationReadModel(stalledId, stalledSubmittedAt);
    ApplicationReadModel assessed =
        reconciliationReadModel(assessedId, threshold.minus(20, ChronoUnit.MINUTES));
    ApplicationReadModel recent = reconciliationReadModel(recentId, threshold.plusSeconds(1));
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(stalled, assessed, recent));
    ApplicationDataPayload stalledData = reconciliationData(null);
    ApplicationDataPayload assessedData = reconciliationData(false);
    ApplicationDataPayload recentData = reconciliationData(null);
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
  void givenDecisionEvent_whenHandled_thenAdvancesVersions() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, "GRANTED", AutoGrantedState.MANUAL, occurredAt));

    assertThat(existing.getApplicationVersion()).isEqualTo(3L);
    assertThat(existing.getApplicationDataVersion()).isEqualTo(4L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenReadyEvent_whenHandled_thenAdvancesReferencedDataVersion() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-21T09:30:00Z");
    ApplicationReadModel existing =
        ApplicationReadModel.builder().applicationId(applicationId).build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    when(applicationReadRepository.save(existing)).thenReturn(existing);

    projection.on(new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, occurredAt));

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
            applicationId,
            1L,
            1L,
            caseworkerId,
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
            applicationId, WorkItemType.APPLICATION, applicationId, 1L, 2L, occurredAt));

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
  void givenIndexReturnsPage_whenFindAllApplicationsQuery_thenBatchLoadsBothSourcesAndAssembles() {
    UUID appId = UUID.randomUUID();
    ApplicationSummaryReadModel indexRow =
        ApplicationSummaryReadModel.builder().applicationId(appId).build();

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
                    ApplicationSummaryReadModel.builder()
                        .applicationId(applicationIdWithPriorAuthority)
                        .build(),
                    ApplicationSummaryReadModel.builder()
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

  private ApplicationReadModel reconciliationReadModel(UUID applicationId, Instant submittedAt) {
    return ApplicationReadModel.builder()
        .applicationId(applicationId)
        .status("APPLICATION_SUBMITTED")
        .applicationDataVersion(2L)
        .applicationVersion(3L)
        .submittedAt(submittedAt)
        .build();
  }

  private ApplicationDataId dataId(ApplicationReadModel application) {
    return new ApplicationDataId(
        application.getApplicationId(), application.getApplicationDataVersion());
  }

  private ApplicationDataPayload reconciliationData(Boolean autoGranted) {
    ApplicationDataPayload data = mock(ApplicationDataPayload.class);
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
            applicationId, 3L, 4L, "REFUSED", AutoGrantedState.MANUAL, occurredAt));

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_REFUSED");
  }

  @Test
  void givenApplicationWithNullSubmittedAt_whenStalledAssessmentQuery_thenExcluded() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID appId = UUID.randomUUID();
    ApplicationReadModel app = reconciliationReadModel(appId, null);
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(app));
    // PENDING auto-grant but null submittedAt — must be excluded by the submittedAt != null filter
    ApplicationDataPayload data = reconciliationData(null);
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
        ApplicationSummaryReadModel.builder().applicationId(appId).build();
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
  @SuppressWarnings("unchecked")
  void givenDataMissingForStateRow_whenFindAllQuery_thenExcludesFromResult() {
    UUID appId = UUID.randomUUID();
    ApplicationSummaryReadModel indexRow =
        ApplicationSummaryReadModel.builder().applicationId(appId).build();
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
  void givenLeadApplication_whenFindingDetail_thenReturnsHydratedApplicationAndRelatedData() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .linkedGroupId(UUID.randomUUID())
            .applicationDataVersion(1L)
            .build();
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
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));
    UUID groupId = application.getLinkedGroupId();
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
    org.mockito.Mockito.verifyNoInteractions(applicationReadQueryGateway, applicationDataStore);
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
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .leadApplicationId(leadApplicationId)
            .memberIds(List.of(leadApplicationId, applicationId))
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));
    group.setGroupId(application.getLinkedGroupId());
    when(groupReadRepository.findById(application.getLinkedGroupId()))
        .thenReturn(Optional.of(group));
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
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .applicationDataVersion(1L)
            .build();
    when(applicationReadRepository.findById(applicationId)).thenReturn(Optional.of(application));
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
  void givenSubmittedApplication_whenFindByIdQuery_thenBuildsModelFromAggregateAndRow() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant groupJoinedAt = Instant.parse("2030-01-01T00:00:00Z");
    ApplicationCreatedEvent created = applicationCreatedEvent(applicationId);
    when(applicationReadRepository.findById(applicationId))
        .thenReturn(
            Optional.of(
                ApplicationReadModel.builder()
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
    org.mockito.Mockito.verifyNoInteractions(applicationReadQueryGateway, applicationDataStore);
  }

  @Test
  void givenApplicationWithMissingData_whenStalledQuery_thenExcluded() {
    Instant threshold = Instant.parse("2026-08-04T09:45:00Z");
    UUID appId = UUID.randomUUID();
    ApplicationReadModel app = reconciliationReadModel(appId, null);
    when(applicationReadRepository.findAllByStatus("APPLICATION_SUBMITTED"))
        .thenReturn(List.of(app));
    // getAll returns empty map — data not found for this application
    when(applicationDataStore.getAll(any())).thenReturn(Map.of());

    StalledAssessments result = projection.handle(new FindStalledAssessmentsQuery(threshold));

    assertThat(result.applications()).isEmpty();
  }
}
