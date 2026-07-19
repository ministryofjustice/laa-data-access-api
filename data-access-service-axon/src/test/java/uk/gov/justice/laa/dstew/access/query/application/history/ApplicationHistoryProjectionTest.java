package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

class ApplicationHistoryProjectionTest {

  private final ObjectMapper objectMapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();
  private ApplicationHistoryReadRepository repository;
  private ApplicationHistoryProjection projection;

  @BeforeEach
  void setUp() {
    repository = mock(ApplicationHistoryReadRepository.class);
    projection = new ApplicationHistoryProjection(repository, objectMapper);
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
  void givenLegacyLinkedEvent_whenHandled_thenRetainsOriginalHistoryContract() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    ApplicationLinkedEvent event =
        new ApplicationLinkedEvent(
            applicationId, UUID.randomUUID(), "{\"legacy\":true}", occurredAt);

    projection.on(event, message(event, "legacy-event-id"));

    ArgumentCaptor<ApplicationHistoryReadModel> captor =
        ArgumentCaptor.forClass(ApplicationHistoryReadModel.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue())
        .extracting(
            ApplicationHistoryReadModel::getEventId,
            ApplicationHistoryReadModel::getApplicationId,
            ApplicationHistoryReadModel::getEventType,
            ApplicationHistoryReadModel::getRequestPayload)
        .containsExactly(
            "legacy-event-id", applicationId, "APPLICATION_LINKED", "{\"legacy\":true}");
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
