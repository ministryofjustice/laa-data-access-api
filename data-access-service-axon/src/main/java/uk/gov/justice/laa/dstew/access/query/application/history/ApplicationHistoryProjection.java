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
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Independently replayable, append-only audit projection of Application events. */
@Component
@ProcessingGroup("application-history-projection")
public class ApplicationHistoryProjection {

  private final ApplicationHistoryReadRepository applicationHistoryReadRepository;
  private final ObjectMapper objectMapper;

  public ApplicationHistoryProjection(
      ApplicationHistoryReadRepository applicationHistoryReadRepository,
      ObjectMapper objectMapper) {
    this.applicationHistoryReadRepository = applicationHistoryReadRepository;
    this.objectMapper = objectMapper;
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
        event.serialisedRequest(),
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

  /** Returns chronologically ordered history rows matching the requested public event types. */
  @QueryHandler
  public java.util.List<ApplicationHistoryReadModel> handle(FindApplicationHistoryQuery query) {
    return applicationHistoryReadRepository
        .findAllByApplicationIdOrderByOccurredAtAsc(query.applicationId())
        .stream()
        .filter(history -> query.eventTypes().contains(history.getEventType()))
        .toList();
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
