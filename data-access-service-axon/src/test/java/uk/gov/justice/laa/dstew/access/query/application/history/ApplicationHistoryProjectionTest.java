package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

@ExtendWith(MockitoExtension.class)
class ApplicationHistoryProjectionTest {

  @Mock private ApplicationDataStore applicationDataStore;

  @Mock private ApplicationHistoryReadRepository repository;

  @Mock private PriorAuthorityHistoryReadRepository paRepository;

  @InjectMocks private ApplicationHistoryProjection projection;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    projection =
        new ApplicationHistoryProjection(
            repository,
            objectMapper,
            applicationDataStore,
            paRepository,
            new PriorAuthorityHistoryAssembler());
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenStoresDistinctHistoryForEveryMember() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            leadId, leadId, List.of(leadId, memberId), occurredAt);
    EventMessage message = message(event, "group-event-id");

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
            Tuple.tuple("group-event-id:" + leadId, leadId, "APPLICATION_GROUP_CREATED"),
            Tuple.tuple("group-event-id:" + memberId, memberId, "APPLICATION_GROUP_JOINED"));
    for (ApplicationHistoryReadModel history : captor.getAllValues()) {
      var payload = objectMapper.readTree(history.getRequestPayload());
      assertThat(payload.get("groupId").asString()).isEqualTo(leadId.toString());
      assertThat(payload.get("leadApplicationId").asString()).isEqualTo(leadId.toString());
      assertThat(payload.get("memberApplicationIds")).hasSize(2);
      assertThat(payload.get("occurredAt")).isNotNull();
      assertThat(history.getServiceName()).isEqualTo("CIVIL_APPLY");
      assertThat(history.getOccurredAt()).isEqualTo(occurredAt);
    }
  }

  @Test
  void givenMemberAddedEvent_whenHandled_thenStoresJoinedHistoryWithSyntheticPayload()
      throws Exception {
    UUID groupId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    MemberAddedToGroupEvent event = new MemberAddedToGroupEvent(groupId, memberId, occurredAt);

    projection.on(event, message(event, "member-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    ApplicationHistoryReadModel history = captor.getValue();
    assertThat(history.getEventId()).isEqualTo("member-event-id:" + memberId);
    assertThat(history.getApplicationId()).isEqualTo(memberId);
    assertThat(history.getEventType()).isEqualTo("APPLICATION_GROUP_JOINED");
    var payload = objectMapper.readTree(history.getRequestPayload());
    assertThat(payload.get("groupId").asString()).isEqualTo(groupId.toString());
    assertThat(payload.get("memberId").asString()).isEqualTo(memberId.toString());
    assertThat(payload.get("occurredAt")).isNotNull();
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

    assertThat(result.applicationEvents()).containsExactly(created);
  }

  @Test
  void givenAssignmentHistory_whenQueried_thenReconstructsCaseworkerAndDescription() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
    ApplicationAssignedToCaseworkerEvent event =
        new ApplicationAssignedToCaseworkerEvent(applicationId, 1L, 2L, caseworkerId, occurredAt);
    projection.on(event, message(event, "assignment-event"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(applicationDataStore.get(applicationId, 2L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withAssignment("Assigned for assessment"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("ASSIGN_APPLICATION_TO_CASEWORKER")));

    assertThat(result.applicationEvents())
        .singleElement()
        .satisfies(
            history -> {
              try {
                var payload = objectMapper.readTree(history.getRequestPayload());
                assertThat(payload.get("caseworkerId").asString())
                    .isEqualTo(caseworkerId.toString());
                assertThat(payload.get("eventDescription").asString())
                    .isEqualTo("Assigned for assessment");
              } catch (Exception exception) {
                throw new AssertionError(exception);
              }
            });
  }

  @Test
  void givenUnassignmentHistory_whenQueried_thenReconstructsDescriptionWithoutCaseworker()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");
    ApplicationUnassignedFromCaseworkerEvent event =
        new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 3L, occurredAt);
    projection.on(event, message(event, "unassignment-event"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(applicationDataStore.get(applicationId, 3L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withAssignment("Returned to queue"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("UNASSIGN_APPLICATION_TO_CASEWORKER")));

    var payload = objectMapper.readTree(result.applicationEvents().getFirst().getRequestPayload());
    assertThat(payload.get("eventDescription").asString()).isEqualTo("Returned to queue");
    assertThat(payload.get("caseworkerId")).isNull();
  }

  @Test
  void givenDecisionHistory_whenQueried_thenReconstructsDescription() throws Exception {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 4L, "GRANTED", AutoGrantedState.MANUAL, occurredAt);
    projection.on(event, message(event, "decision-event"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    when(applicationDataStore.get(applicationId, 4L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withDecision(
                    "GRANTED", AutoGrantedState.MANUAL, Map.of(), null, "{}", "Decision recorded"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("APPLICATION_MAKE_DECISION_GRANTED")));

    var payload = objectMapper.readTree(result.applicationEvents().getFirst().getRequestPayload());
    assertThat(payload.get("eventDescription").asString()).isEqualTo("Decision recorded");
  }

  @Test
  void givenNoteCreatedEvent_whenHandled_thenStoresNoteCreatedHistory() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    NoteCreatedEvent event = new NoteCreatedEvent(applicationId, 1L, occurredAt);

    projection.on(event, message(event, "note-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("APPLICATION_NOTE_CREATED");
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getOccurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenNoteHistory_whenQueried_thenReconstructsNoteText() throws Exception {
    UUID applicationId = UUID.randomUUID();
    NoteCreatedEvent event = new NoteCreatedEvent(applicationId, 1L, Instant.now());
    projection.on(event, message(event, "note-event-id"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of());
    ApplicationDataPayload payloadWithNote =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId))
            .withNote("My note text", Instant.now());
    when(applicationDataStore.get(applicationId, 1L)).thenReturn(payloadWithNote);

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_NOTE_CREATED")));

    var payload = objectMapper.readTree(result.applicationEvents().getFirst().getRequestPayload());
    assertThat(payload.get("noteText").asString()).isEqualTo("My note text");
  }

  @Test
  void givenPriorAuthorityCreatedEvent_whenHandled_thenStoresInPaHistoryTable() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");
    var event =
        new PriorAuthorityCreatedEvent(
            submissionId, applicationId, "EXPERT", 0L, "fp", "PENDING", 1, occurredAt);
    var msg = message(event, "pa-event-id");

    projection.on(event, msg);

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getEventId()).isEqualTo("pa-event-id");
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getSubmissionId()).isEqualTo(submissionId);
    assertThat(saved.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(saved.getEventType()).isEqualTo("PRIOR_AUTHORITY_CREATED");
    assertThat(saved.getServiceName()).isEqualTo("CIVIL_APPLY");
    assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(saved.getEventData()).contains("\"status\":\"PENDING\"");
    assertThat(saved.getEventData()).contains("\"dataVersion\":0");
  }

  @Test
  void givenApplicationWithPriorAuthorities_whenQueried_thenReturnsBothEventSets() {
    UUID applicationId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    var applicationEvent =
        history(applicationId, "APPLICATION_CREATED", Instant.parse("2026-08-05T09:00:00Z"));
    var priorAuthorityEvent = paHistoryReadModel(applicationId, submissionId);
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(applicationEvent));
    when(paRepository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(priorAuthorityEvent));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_CREATED")));

    assertThat(result.applicationEvents()).hasSize(1);
    assertThat(result.priorAuthorities())
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.submissionId()).isEqualTo(submissionId);
              assertThat(group.priorAuthorityType()).isEqualTo("EXPERT");
              assertThat(group.events())
                  .extracting(PriorAuthorityHistoryEventResult::eventType)
                  .containsExactly("PRIOR_AUTHORITY_CREATED");
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

    assertThat(result.applicationEvents()).isEmpty();
    assertThat(result.priorAuthorities())
        .singleElement()
        .satisfies(group -> assertThat(group.events()).hasSize(1));
  }

  @Test
  void givenReset_whenHandled_thenDeletesBothHistoryTables() {
    projection.reset();
    verify(repository).deleteAllInBatch();
    verify(paRepository).deleteAllInBatch();
  }

  private PriorAuthorityHistoryReadModel paHistoryReadModel(UUID applicationId, UUID submissionId) {
    return PriorAuthorityHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(applicationId)
        .submissionId(submissionId)
        .priorAuthorityType("EXPERT")
        .eventType("PRIOR_AUTHORITY_CREATED")
        .eventData("{\"status\":\"PENDING\",\"dataVersion\":0}")
        .serviceName("CIVIL_APPLY")
        .occurredAt(Instant.parse("2026-08-05T10:00:00Z"))
        .build();
  }

  private EventMessage message(Object payload, String identifier) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY"),
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private ApplicationHistoryReadModel history(
      UUID applicationId, String eventType, Instant occurredAt) {
    return ApplicationHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(applicationId)
        .eventType(eventType)
        .requestPayload("{}")
        .occurredAt(occurredAt)
        .build();
  }
}
