package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.AssignmentChange;
import uk.gov.justice.laa.dstew.access.query.groupassignment.LinkedGroupAssignmentTracker;

@ExtendWith(MockitoExtension.class)
class ApplicationHistoryProjectionTest {

  @Mock private ApplicationDataStore applicationDataStore;

  @Mock private ApplicationHistoryReadRepository repository;

  @Mock private PriorAuthorityHistoryReadRepository paRepository;

  @Mock private PriorAuthorityDataRepository priorAuthorityDataRepository;
  @Mock private ApplicationHistoryGroupAssignmentStore assignmentStore;
  @Mock private LinkedGroupAssignmentTracker assignmentTracker;

  private ApplicationHistoryProjection projection;

  @BeforeEach
  void setUp() {
    projection =
        new ApplicationHistoryProjection(
            repository,
            new ApplicationHistoryAssembler(applicationDataStore),
            paRepository,
            new PriorAuthorityHistoryAssembler(
                new PriorAuthorityDataStore(priorAuthorityDataRepository)),
            assignmentTracker,
            assignmentStore);
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenStoresDistinctHistoryForEveryMember() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    List<UUID> memberIds = List.of(leadId, memberId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(leadId, leadId, memberIds, occurredAt);
    when(assignmentTracker.groupCreated(assignmentStore, leadId, memberIds)).thenReturn(List.of());

    projection.on(event, message(event, "group-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType,
            ApplicationHistoryReadModel::getDataVersion,
            ApplicationHistoryReadModel::getServiceName)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "group-event-id:" + leadId,
                leadId,
                "APPLICATION_GROUP_CREATED",
                null,
                "CIVIL_APPLY"),
            Tuple.tuple(
                "group-event-id:" + memberId,
                memberId,
                "APPLICATION_GROUP_JOINED",
                null,
                "CIVIL_APPLY"));
    assertThat(captor.getAllValues())
        .extracting(ApplicationHistoryReadModel::getOccurredAt)
        .containsOnly(occurredAt);
    verify(assignmentTracker).groupCreated(assignmentStore, leadId, memberIds);
  }

  @Test
  void givenMemberAddedEvent_whenHandled_thenStoresJoinedHistoryWithoutDataVersion() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    MemberAddedToGroupEvent event =
        new MemberAddedToGroupEvent(
            groupId, UUID.randomUUID(), memberId, Instant.parse("2026-07-15T08:00:00Z"));
    when(assignmentTracker.memberAdded(assignmentStore, groupId, memberId)).thenReturn(List.of());

    projection.on(event, message(event, "member-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    ApplicationHistoryReadModel history = captor.getValue();
    assertThat(history.getEventId()).isEqualTo("member-event-id:" + memberId);
    assertThat(history.getApplicationId()).isEqualTo(memberId);
    assertThat(history.getEventType()).isEqualTo("APPLICATION_GROUP_JOINED");
    assertThat(history.getDataVersion()).isNull();
    assertThat(history.getOccurredAt()).isEqualTo(event.occurredAt());
    verify(assignmentTracker).memberAdded(assignmentStore, groupId, memberId);
  }

  @Test
  void givenMemberJoinsAssignedGroup_whenProjected_thenAssignRowAppended() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    MemberAddedToGroupEvent event =
        new MemberAddedToGroupEvent(groupId, UUID.randomUUID(), memberId, occurredAt);
    when(assignmentTracker.memberAdded(assignmentStore, groupId, memberId))
        .thenReturn(List.of(new AssignmentChange(memberId, groupId, null, caseworkerId, 3L)));
    EventMessage message = message(event, "member-join-event");

    projection.on(event, message);

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("member-join-event:" + memberId, memberId, "APPLICATION_GROUP_JOINED"),
            Tuple.tuple(
                "member-join-event:" + memberId + ":assignment",
                memberId,
                "ASSIGN_APPLICATION_TO_CASEWORKER"));
  }

  @Test
  void givenLeadChangedEvent_whenHandled_thenStoresHistoryForBothLeads() {
    UUID groupId = UUID.randomUUID();
    UUID previousLeadId = UUID.randomUUID();
    UUID newLeadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupLeadChangedEvent event =
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, previousLeadId, newLeadId, 1L, occurredAt);
    EventMessage message = message(event, "lead-changed-event-id");

    projection.on(event, message);

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "lead-changed-event-id:" + newLeadId, newLeadId, "APPLICATION_GROUP_LEAD_CHANGED"),
            Tuple.tuple(
                "lead-changed-event-id:" + previousLeadId,
                previousLeadId,
                "APPLICATION_GROUP_LEAD_CHANGED"));
    assertThat(captor.getAllValues())
        .allSatisfy(
            history -> {
              assertThat(history.getDataVersion()).isNull();
              assertThat(history.getOccurredAt()).isEqualTo(occurredAt);
            });
  }

  @Test
  void givenMemberRemovedEvent_whenHandled_thenStoresLeftHistoryForMember() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    MemberRemovedFromGroupEvent event =
        new MemberRemovedFromGroupEvent(groupId, leadId, memberId, 1L, null, 0L, occurredAt);

    projection.on(event, message(event, "member-removed-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventId()).isEqualTo("member-removed-event-id:" + memberId);
    assertThat(captor.getValue().getApplicationId()).isEqualTo(memberId);
    assertThat(captor.getValue().getEventType()).isEqualTo("APPLICATION_GROUP_LEFT");
    verify(assignmentTracker).membersLeft(assignmentStore, groupId, List.of(memberId), false);
  }

  @Test
  void givenDissolvedEvent_whenHandled_thenStoresLeftAndDissolvedHistory() {
    UUID groupId = UUID.randomUUID();
    UUID formerLeadId = UUID.randomUUID();
    UUID removedApplicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupDissolvedEvent event =
        new LinkedApplicationGroupDissolvedEvent(
            groupId,
            formerLeadId,
            removedApplicationId,
            List.of(formerLeadId, removedApplicationId),
            1L,
            null,
            0L,
            occurredAt);
    EventMessage message = message(event, "group-dissolved-event-id");

    projection.on(event, message);

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "group-dissolved-event-id:" + removedApplicationId,
                removedApplicationId,
                "APPLICATION_GROUP_LEFT"),
            Tuple.tuple(
                "group-dissolved-event-id:" + formerLeadId,
                formerLeadId,
                "APPLICATION_GROUP_DISSOLVED"));
    verify(assignmentTracker)
        .membersLeft(assignmentStore, groupId, List.of(formerLeadId, removedApplicationId), true);
  }

  @Test
  void givenLinkedGroupHistoryTypes_whenQueried_thenReturnsRequestedEvents() {
    UUID applicationId = UUID.randomUUID();
    var leadChanged =
        history(
            applicationId, "APPLICATION_GROUP_LEAD_CHANGED", Instant.parse("2026-07-19T10:00:00Z"));
    var left =
        history(applicationId, "APPLICATION_GROUP_LEFT", Instant.parse("2026-07-19T10:01:00Z"));
    var dissolved =
        history(
            applicationId, "APPLICATION_GROUP_DISSOLVED", Instant.parse("2026-07-19T10:02:00Z"));
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(leadChanged, left, dissolved));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId,
                List.of(
                    "APPLICATION_GROUP_LEAD_CHANGED",
                    "APPLICATION_GROUP_LEFT",
                    "APPLICATION_GROUP_DISSOLVED")));

    assertThat(result.applicationHistoryEvents())
        .extracting(
            ApplicationHistoryEventResult::eventType, ApplicationHistoryEventResult::occurredAt)
        .containsExactly(
            Tuple.tuple("APPLICATION_GROUP_LEAD_CHANGED", leadChanged.getOccurredAt()),
            Tuple.tuple("APPLICATION_GROUP_LEFT", left.getOccurredAt()),
            Tuple.tuple("APPLICATION_GROUP_DISSOLVED", dissolved.getOccurredAt()));
  }

  @Test
  void givenReset_whenHandled_thenDeletesHistory() {
    projection.reset();

    verify(repository).deleteAllInBatch();
  }

  @Test
  void givenHistoryQuery_whenHandled_thenReturnsOnlyRequestedApiEventTypes() {
    UUID applicationId = UUID.randomUUID();
    ApplicationHistoryReadModel created =
        history(applicationId, "APPLICATION_CREATED", Instant.parse("2026-07-19T10:00:00Z"));
    ApplicationHistoryReadModel internalGroupEvent =
        history(applicationId, "APPLICATION_GROUP_JOINED", Instant.parse("2026-07-19T10:01:00Z"));
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(created, internalGroupEvent));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_CREATED")));

    assertThat(result.applicationHistoryEvents())
        .extracting(ApplicationHistoryEventResult::eventType)
        .containsExactly("APPLICATION_CREATED");
  }

  @Test
  void givenApplicationWorkItemAssigned_whenHandled_thenStoresMetadataCaseworkerId() {
    UUID applicationId = UUID.randomUUID();
    UUID eventCaseworkerId = UUID.randomUUID();
    UUID metadataCaseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    WorkItemAssigned event =
        new WorkItemAssigned(
            applicationId, WorkItemType.APPLICATION, 1L, 1L, eventCaseworkerId, occurredAt);

    projection.on(event, messageWithCaseworker(event, "assignment-event", metadataCaseworkerId));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getCaseworkerId()).isEqualTo(metadataCaseworkerId);
    assertThat(captor.getValue().getDataVersion()).isNull();
    verify(assignmentTracker)
        .directAssignmentChanged(assignmentStore, applicationId, eventCaseworkerId, 1L);
  }

  @Test
  void givenPriorAuthorityAssignment_whenHandled_thenDoesNotTrackAsApplicationHistory() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");

    projection.on(
        new WorkItemAssigned(
            priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, UUID.randomUUID(), occurredAt),
        message(new Object(), "prior-authority-assigned"));

    verify(repository, never()).save(any());
    verify(assignmentTracker, never()).directAssignmentChanged(any(), any(), any(), anyLong());
  }

  @Test
  void givenApplicationWorkItemUnassigned_whenHandled_thenStoresMetadataCaseworkerId() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkItemUnassigned event =
        new WorkItemUnassigned(
            applicationId, WorkItemType.APPLICATION, 1L, 2L, Instant.parse("2026-07-20T09:00:00Z"));

    projection.on(event, messageWithCaseworker(event, "unassignment-event", caseworkerId));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(captor.getValue().getDataVersion()).isNull();
    verify(assignmentTracker).directAssignmentChanged(assignmentStore, applicationId, null, 2L);
  }

  @Test
  void givenPriorAuthorityUnassignment_whenHandled_thenDoesNotTrackAsApplicationHistory() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");

    projection.on(
        new WorkItemUnassigned(priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, occurredAt),
        message(new Object(), "prior-authority-unassigned"));

    verify(repository, never()).save(any());
    verify(assignmentTracker, never()).directAssignmentChanged(any(), any(), any(), anyLong());
  }

  @Test
  void givenDecisionHistory_whenQueried_thenReconstructsDescription() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId,
            1L,
            4L,
            "GRANTED",
            AutoGrantedState.MANUAL,
            Instant.parse("2026-07-20T10:00:00Z"));
    projection.on(event, message(event, "decision-event"));
    verify(assignmentTracker).applicationDecided(assignmentStore, applicationId);
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDataVersion()).isEqualTo(4L);
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(applicationDataStore.findPayload(applicationId, 4L))
        .thenReturn(
            Optional.of(
                ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                    .withDecision(
                        "GRANTED",
                        AutoGrantedState.MANUAL,
                        Map.of(),
                        null,
                        "{}",
                        "Decision recorded")));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("APPLICATION_MAKE_DECISION_GRANTED")));

    assertThat(result.applicationHistoryEvents().getFirst().eventDescription())
        .isEqualTo("Decision recorded");
  }

  @Test
  void givenNoteCreatedEvent_whenHandled_thenStoresNoteDataVersion() {
    UUID applicationId = UUID.randomUUID();
    NoteCreatedEvent event =
        new NoteCreatedEvent(applicationId, 1L, Instant.parse("2026-07-20T10:00:00Z"));

    projection.on(event, message(event, "note-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("APPLICATION_NOTE_CREATED");
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getDataVersion()).isEqualTo(1L);
    assertThat(captor.getValue().getOccurredAt()).isEqualTo(event.occurredAt());
  }

  @Test
  void givenNoteHistory_whenQueried_thenReturnsNoteTextAsDescription() {
    UUID applicationId = UUID.randomUUID();
    NoteCreatedEvent event =
        new NoteCreatedEvent(applicationId, 1L, Instant.parse("2026-07-20T10:00:00Z"));
    projection.on(event, message(event, "note-event-id"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(applicationDataStore.findPayload(applicationId, 1L))
        .thenReturn(
            Optional.of(
                ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                    .withNote("My note text", Instant.parse("2026-07-20T09:00:00Z"))));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_NOTE_CREATED")));

    var history = result.applicationHistoryEvents().getFirst();
    assertThat(history.eventType()).isEqualTo("APPLICATION_NOTE_CREATED");
    assertThat(history.eventDescription()).isEqualTo("My note text");
  }

  @Test
  void givenApplicationWithPriorAuthorities_whenQueried_thenReturnsBothEventSets() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    var applicationEvent =
        history(applicationId, "APPLICATION_CREATED", Instant.parse("2026-08-05T09:00:00Z"));
    var priorAuthorityEvent = paHistoryReadModel(applicationId, priorAuthorityId);
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(applicationEvent));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(priorAuthorityEvent));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_CREATED")));

    assertThat(result.applicationHistoryEvents())
        .extracting(ApplicationHistoryEventResult::eventType)
        .containsExactly("APPLICATION_CREATED");
    assertThat(result.priorAuthorityHistoryGroups())
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.priorAuthorityId()).isEqualTo(priorAuthorityId);
              assertThat(group.priorAuthorityType()).isEqualTo("EXPERT");
              assertThat(group.events())
                  .extracting(PriorAuthorityHistoryEventResult::eventType)
                  .containsExactly("PRIOR_AUTHORITY_SUBMITTED");
            });
  }

  @Test
  void givenPriorAuthorityEvents_whenQueried_thenPaEventsNotFilteredByEventTypeParam() {
    UUID applicationId = UUID.randomUUID();
    var priorAuthorityEvent = paHistoryReadModel(applicationId, UUID.randomUUID());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(priorAuthorityEvent));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_CREATED")));

    assertThat(result.applicationHistoryEvents()).isEmpty();
    assertThat(result.priorAuthorityHistoryGroups())
        .singleElement()
        .satisfies(group -> assertThat(group.events()).hasSize(1));
  }

  @Test
  void givenReset_whenHandled_thenDeletesBothHistoryTables() {
    projection.reset();

    verify(repository).deleteAllInBatch();
    verify(paRepository).deleteAllInBatch();
    verify(assignmentStore).deleteAll();
  }

  @Test
  void givenProjection_whenInspected_thenProcessesInOneLane() {
    assertThat(ApplicationHistoryProjection.class.isAnnotationPresent(SequencingPolicy.class))
        .isTrue();
  }

  @Test
  void givenReadyEvent_whenProjected_thenTrackerActivatesWithoutHistoryRow() {
    UUID applicationId = UUID.randomUUID();

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(
            applicationId, 2L, 3L, 4L, Instant.parse("2026-09-30T12:00:00Z")));

    verify(assignmentTracker).applicationReady(assignmentStore, applicationId, 4L);
    verify(repository, never()).save(any());
  }

  @Test
  void givenChangedAndUnchangedMembers_whenGroupAssigned_thenWritesOnlyChangedCaseworkerHistory() {
    UUID groupId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID unchangedId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    LinkedGroupAssignedEvent event =
        new LinkedGroupAssignedEvent(groupId, caseworkerId, 2L, occurredAt);
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, caseworkerId, 2L))
        .thenReturn(
            List.of(
                new AssignmentChange(applicationId, groupId, null, caseworkerId, 2L),
                new AssignmentChange(unchangedId, groupId, caseworkerId, caseworkerId, 2L)));

    projection.on(event, messageWithCaseworker(event, "assignment-event", actorId));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    ApplicationHistoryReadModel row = captor.getValue();
    assertThat(row.getEventId()).isEqualTo("assignment-event:" + applicationId + ":assignment");
    assertThat(row.getApplicationId()).isEqualTo(applicationId);
    assertThat(row.getEventType()).isEqualTo("ASSIGN_APPLICATION_TO_CASEWORKER");
    assertThat(row.getDataVersion()).isNull();
    assertThat(row.getServiceName()).isEqualTo("CIVIL_APPLY");
    assertThat(row.getCaseworkerId()).isEqualTo(actorId);
    assertThat(row.getOccurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenClearedCaseworker_whenGroupUnassigned_thenUnassignRowAppended() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    LinkedGroupUnassignedEvent event = new LinkedGroupUnassignedEvent(groupId, 3L, occurredAt);
    when(assignmentTracker.groupAssignmentChanged(assignmentStore, groupId, null, 3L))
        .thenReturn(List.of(new AssignmentChange(applicationId, groupId, caseworkerId, null, 3L)));

    projection.on(event, message(event, "unassignment-event"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("UNASSIGN_APPLICATION_TO_CASEWORKER");
  }

  @Test
  void givenInheritedAssignment_whenProjected_thenAssignRowAppended() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    LinkedGroupMemberAssignmentInheritedEvent event =
        new LinkedGroupMemberAssignmentInheritedEvent(
            groupId, applicationId, caseworkerId, 2L, occurredAt);
    when(assignmentTracker.memberInherited(
            assignmentStore, groupId, applicationId, caseworkerId, 2L))
        .thenReturn(
            Optional.of(new AssignmentChange(applicationId, groupId, null, caseworkerId, 2L)));

    projection.on(event, message(event, "inherited-event"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("ASSIGN_APPLICATION_TO_CASEWORKER");
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenChangedCaseworker_whenReclaimed_thenAssignmentRowAppended() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    ApplicationAssignmentReclaimedFromGroupEvent event =
        new ApplicationAssignmentReclaimedFromGroupEvent(
            applicationId, UUID.randomUUID(), caseworkerId, 4L, occurredAt);
    when(assignmentTracker.assignmentReclaimed(assignmentStore, applicationId, caseworkerId, 4L))
        .thenReturn(Optional.of(new AssignmentChange(applicationId, null, null, caseworkerId, 4L)));

    projection.on(event, message(event, "reclaimed-event"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("ASSIGN_APPLICATION_TO_CASEWORKER");
  }

  @Test
  void givenUnchangedCaseworker_whenReclaimed_thenNoRowAppended() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-30T12:00:00Z");
    ApplicationAssignmentReclaimedFromGroupEvent event =
        new ApplicationAssignmentReclaimedFromGroupEvent(
            applicationId, UUID.randomUUID(), caseworkerId, 4L, occurredAt);
    when(assignmentTracker.assignmentReclaimed(assignmentStore, applicationId, caseworkerId, 4L))
        .thenReturn(
            Optional.of(new AssignmentChange(applicationId, null, caseworkerId, caseworkerId, 4L)));

    projection.on(event, message(event, "unchanged-reclaimed-event"));

    verify(repository, never()).save(any());
  }

  private PriorAuthorityHistoryReadModel paHistoryReadModel(
      UUID applicationId, UUID priorAuthorityId) {
    return PriorAuthorityHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(applicationId)
        .priorAuthorityId(priorAuthorityId)
        .priorAuthorityType("EXPERT")
        .eventType("PRIOR_AUTHORITY_SUBMITTED")
        .itemVersion(2L)
        .serviceName("CIVIL_APPLY")
        .occurredAt(Instant.parse("2026-08-05T10:00:00Z"))
        .build();
  }

  private EventMessage message(Object payload, String identifier) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY"),
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private EventMessage messageWithCaseworker(Object payload, String identifier, UUID caseworkerId) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(
            RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
            "CIVIL_APPLY",
            RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY,
            caseworkerId.toString()),
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private ApplicationHistoryReadModel history(
      UUID applicationId, String eventType, Instant occurredAt) {
    return ApplicationHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(applicationId)
        .eventType(eventType)
        .occurredAt(occurredAt)
        .build();
  }
}
