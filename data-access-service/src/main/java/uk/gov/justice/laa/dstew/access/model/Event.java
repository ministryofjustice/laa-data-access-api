package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;

/**
 * Represents a domain event in the application.
 * This record encapsulates the details of an event, including its type,
 * associated application ID, timestamp, description, caseworker ID, domain event ID, and request payload.
 * It provides static methods to convert between different representations of domain events,
 * such as from DynamoDB entities and attribute maps.
 */
@Builder
public record Event(DomainEventType eventType, String applicationId, Instant timestamp, String description, String caseworkerId,
                    String requestPayload) {

  /**
   * Converts a DomainEventDynamoDB entity to an Event record.
   *
   * @param entity the DomainEventDynamoDB entity to convert
   * @return an Event record containing the data from the DomainEventDynamoDB entity
   */
  public static Event fromDynamoEntity(DomainEventDynamoDb entity) {
    if (entity == null) {
      return null;
    }
    return Event.builder()
        .eventType(DomainEventType.valueOf(entity.getType()))
        .applicationId(extractEventIdFromPk(entity.getPk()))
        .timestamp(Instant.parse(entity.getCreatedAt()))
        .caseworkerId(entity.getCaseworkerId())
        .description(entity.getType())
        .requestPayload(entity.getDescription())
        .build();
  }

  /**
   * Converts an Event record to an ApplicationDomainEvent entity.
   *
   * @param event the Event record to convert
   * @return an ApplicationDomainEvent entity containing the data from the Event record
   */
  public static ApplicationDomainEvent fromEvent(Event event) {
    ApplicationDomainEvent domainEvent = new ApplicationDomainEvent();
    domainEvent.setApplicationId(java.util.UUID.fromString(event.applicationId()));
    domainEvent.setDomainEventType(event.eventType());
    domainEvent.setCreatedAt(OffsetDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC));
    domainEvent.setCreatedBy(event.caseworkerId());
    domainEvent.setCaseworkerId(UUID.fromString(event.caseworkerId()));
    domainEvent.setEventDescription(event.description());
    return domainEvent;

  }

  /**
   * Converts a DomainEventEntity to an Event record.
   *
   * @param domainEventEntity the DomainEventEntity to convert
   * @return an Event record containing the data from the DomainEventEntity
   */
  public static Event convertToEvent(DomainEventEntity domainEventEntity) {
    return Event.builder()
        .eventType(domainEventEntity.getType())
        .applicationId(String.valueOf(domainEventEntity.getApplicationId()))
        .caseworkerId(domainEventEntity.getCaseworkerId() != null ? String.valueOf(domainEventEntity.getCaseworkerId()) : null)
        .timestamp(domainEventEntity.getCreatedAt())
        .description(domainEventEntity.getType().name())
        .requestPayload(domainEventEntity.getData())
        .build();
  }

  private static String extractEventIdFromPk(String pk) {
    return pk != null && pk.contains("#") ? pk.split("#", 2)[1] : pk;
  }
}
