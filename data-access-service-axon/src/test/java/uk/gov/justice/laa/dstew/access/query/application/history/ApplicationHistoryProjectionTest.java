package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

class ApplicationHistoryProjectionTest {

  private final ObjectMapper objectMapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();
  private ApplicationHistoryReadRepository repository;
  private ApplicationDataStore applicationDataStore;
  private ApplicationHistoryProjection projection;

  @BeforeEach
  void setUp() {
    repository = mock(ApplicationHistoryReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    projection = new ApplicationHistoryProjection(repository, objectMapper, applicationDataStore);
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenStoresDistinctHistoryForEveryMember()
      throws Exception {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            leadId, leadId, List.of(leadId, memberId), occurredAt);
    EventMessage<?> message = message(event, "group-event-id");

    projection.on(event, message);

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "group-event-id:" + leadId, leadId, "APPLICATION_GROUP_CREATED"),
            org.assertj.core.groups.Tuple.tuple(
                "group-event-id:" + memberId, memberId, "APPLICATION_GROUP_JOINED"));
    for (ApplicationHistoryReadModel history : captor.getAllValues()) {
      var payload = objectMapper.readTree(history.getRequestPayload());
      assertThat(payload.get("groupId").asText()).isEqualTo(leadId.toString());
      assertThat(payload.get("leadApplicationId").asText()).isEqualTo(leadId.toString());
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
    assertThat(payload.get("groupId").asText()).isEqualTo(groupId.toString());
    assertThat(payload.get("memberId").asText()).isEqualTo(memberId.toString());
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

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(applicationId, List.of("APPLICATION_CREATED")));

    assertThat(result).containsExactly(created);
  }

  @Test
  void givenAssignmentHistory_whenQueried_thenReconstructsCaseworkerAndDescription()
      throws Exception {
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
    when(applicationDataStore.get(applicationId, 2L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withAssignment("Assigned for assessment"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("ASSIGN_APPLICATION_TO_CASEWORKER")));

    assertThat(result)
        .singleElement()
        .satisfies(
            history -> {
              try {
                var payload = objectMapper.readTree(history.getRequestPayload());
                assertThat(payload.get("caseworkerId").asText()).isEqualTo(caseworkerId.toString());
                assertThat(payload.get("eventDescription").asText())
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
    when(applicationDataStore.get(applicationId, 3L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withAssignment("Returned to queue"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("UNASSIGN_APPLICATION_TO_CASEWORKER")));

    var payload = objectMapper.readTree(result.getFirst().getRequestPayload());
    assertThat(payload.get("eventDescription").asText()).isEqualTo("Returned to queue");
    assertThat(payload.get("caseworkerId")).isNull();
  }

  @Test
  void givenDecisionHistory_whenQueried_thenReconstructsDescription() throws Exception {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(applicationId, 1L, 4L, "GRANTED", false, occurredAt);
    projection.on(event, message(event, "decision-event"));
    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    when(repository.findAllByApplicationIdOrderByOccurredAtAsc(applicationId))
        .thenReturn(List.of(captor.getValue()));
    when(applicationDataStore.get(applicationId, 4L))
        .thenReturn(
            ApplicationDataPayload.from(applicationCreationDetails(applicationId))
                .withDecision("GRANTED", false, Map.of(), null, "{}", "Decision recorded"));

    var result =
        projection.handle(
            new FindApplicationHistoryQuery(
                applicationId, List.of("APPLICATION_MAKE_DECISION_GRANTED")));

    var payload = objectMapper.readTree(result.getFirst().getRequestPayload());
    assertThat(payload.get("eventDescription").asText()).isEqualTo("Decision recorded");
  }

  private EventMessage<?> message(Object payload, String identifier) {
    return new GenericEventMessage<>(
        identifier,
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
