package uk.gov.justice.laa.dstew.access.query.application.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Independently replayable, append-only audit projection of Application events. */
@Component
@ProcessingGroup("application-history-projection")
public class ApplicationHistoryProjection {

  private final ApplicationHistoryReadRepository applicationHistoryReadRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationDataStore applicationDataStore;

  /** Creates the history projection with its persistence and reconstruction dependencies. */
  public ApplicationHistoryProjection(
      ApplicationHistoryReadRepository applicationHistoryReadRepository,
      ObjectMapper objectMapper,
      ApplicationDataStore applicationDataStore) {
    this.applicationHistoryReadRepository = applicationHistoryReadRepository;
    this.objectMapper = objectMapper;
    this.applicationDataStore = applicationDataStore;
  }

  /**
   * Appends an audit entry when an Application is created.
   *
   * @param event the Application creation event
   * @param message the Axon message carrying event metadata
   */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_CREATED",
        serialise(event),
        event.occurredAt());
  }

  /**
   * Appends an audit entry when a linked application group is created.
   *
   * <p>Records {@code APPLICATION_GROUP_CREATED} against the lead application and {@code
   * APPLICATION_GROUP_JOINED} against each non-lead member.
   *
   * @param event the group creation event
   * @param message the Axon message carrying event metadata
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, EventMessage<?> message) {
    String requestPayload = serialise(event);
    append(
        message,
        event.leadApplicationId(),
        "APPLICATION_GROUP_CREATED",
        requestPayload,
        event.occurredAt(),
        groupHistoryId(message, event.leadApplicationId()));
    event.memberApplicationIds().stream()
        .filter(id -> !id.equals(event.leadApplicationId()))
        .forEach(
            memberId ->
                append(
                    message,
                    memberId,
                    "APPLICATION_GROUP_JOINED",
                    requestPayload,
                    event.occurredAt(),
                    groupHistoryId(message, memberId)));
  }

  /** Appends an audit entry when an application joins an existing linked application group. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event, EventMessage<?> message) {
    append(
        message,
        event.memberId(),
        "APPLICATION_GROUP_JOINED",
        serialise(event),
        event.occurredAt(),
        groupHistoryId(message, event.memberId()));
  }

  /** Appends a thin audit entry for an Application decision. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "GRANTED".equals(event.overallDecision())
            ? "APPLICATION_MAKE_DECISION_GRANTED"
            : "APPLICATION_MAKE_DECISION_REFUSED",
        serialise(event),
        event.occurredAt());
  }

  /** Appends a thin audit entry for a caseworker assignment. */
  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "ASSIGN_APPLICATION_TO_CASEWORKER",
        serialise(event),
        event.occurredAt());
  }

  /** Appends a thin audit entry for a caseworker unassignment. */
  @EventHandler
  public void on(ApplicationUnassignedFromCaseworkerEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "UNASSIGN_APPLICATION_TO_CASEWORKER",
        serialise(event),
        event.occurredAt());
  }

  /** Returns chronologically ordered history rows matching the requested public event types. */
  @QueryHandler
  public java.util.List<ApplicationHistoryReadModel> handle(FindApplicationHistoryQuery query) {
    return applicationHistoryReadRepository
        .findAllByApplicationIdOrderByOccurredAtAsc(query.applicationId())
        .stream()
        .filter(history -> query.eventTypes().contains(history.getEventType()))
        .map(this::hydrateEventDescription)
        .toList();
  }

  private ApplicationHistoryReadModel hydrateEventDescription(ApplicationHistoryReadModel history) {
    boolean decision = history.getEventType().startsWith("APPLICATION_MAKE_DECISION_");
    boolean assignment = "ASSIGN_APPLICATION_TO_CASEWORKER".equals(history.getEventType());
    boolean unassignment = "UNASSIGN_APPLICATION_TO_CASEWORKER".equals(history.getEventType());
    if (!decision && !assignment && !unassignment) {
      return history;
    }
    try {
      var thinPayload = objectMapper.readTree(history.getRequestPayload());
      long version = thinPayload.get("applicationDataVersion").asLong();
      var data = applicationDataStore.get(history.getApplicationId(), version);
      String description =
          decision ? data.decisionEventDescription() : data.assignmentEventDescription();
      java.util.Map<String, Object> reconstructedPayload = new java.util.HashMap<>();
      reconstructedPayload.put("eventDescription", description);
      if (assignment && thinPayload.get("caseworkerId") != null) {
        reconstructedPayload.put("caseworkerId", thinPayload.get("caseworkerId").asText());
      }
      return ApplicationHistoryReadModel.builder()
          .eventId(history.getEventId())
          .applicationId(history.getApplicationId())
          .eventType(history.getEventType())
          .requestPayload(objectMapper.writeValueAsString(reconstructedPayload))
          .serviceName(history.getServiceName())
          .occurredAt(history.getOccurredAt())
          .build();
    } catch (Exception exception) {
      return history;
    }
  }

  @ResetHandler
  public void reset() {
    applicationHistoryReadRepository.deleteAllInBatch();
  }

  private void append(
      EventMessage<?> message,
      UUID applicationId,
      String eventType,
      String requestPayload,
      Instant occurredAt) {
    append(message, applicationId, eventType, requestPayload, occurredAt, message.getIdentifier());
  }

  private void append(
      EventMessage<?> message,
      UUID applicationId,
      String eventType,
      String requestPayload,
      Instant occurredAt,
      String historyId) {
    Object serviceName =
        message.getMetaData().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    applicationHistoryReadRepository.save(
        ApplicationHistoryReadModel.builder()
            .eventId(historyId)
            .applicationId(applicationId)
            .eventType(eventType)
            .requestPayload(requestPayload)
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(occurredAt)
            .build());
  }

  private String groupHistoryId(EventMessage<?> message, UUID applicationId) {
    return message.getIdentifier() + ":" + applicationId;
  }

  private String serialise(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to serialise linked application group event", exception);
    }
  }
}
