package uk.gov.justice.laa.dstew.access.service.impl;

import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;
import uk.gov.justice.laa.dstew.access.model.Event;

public class EventHistoryServiceAwsImpl implements EventHistoryService {
  private final DynamoDbService dynamoDbService;

  public EventHistoryServiceAwsImpl(DynamoDbService dynamoDbService) {
    this.dynamoDbService = dynamoDbService;
  }

  /**
   * Provides a list of events associated with an application in createdAt ascending order.
   */
  @Override
  public List<ApplicationDomainEvent> getEvents(UUID applicationId,
                                                @Valid List<DomainEventType> eventType) {

    if(eventType == null || eventType.isEmpty()) {
      return dynamoDbService.getAllApplicationsById(String.valueOf(applicationId)).stream()
          .map(Event::fromDynamoEntity)
          .map(Event::fromEvent)
          .sorted(Comparator.comparing(ApplicationDomainEvent::getCreatedAt))
          .toList();
    }
    return dynamoDbService.getAllApplicationsByIdAndEventType(String.valueOf(applicationId), eventType).stream()
        .map(Event::fromDynamoEntity)
        .map(Event::fromEvent)
        .sorted(Comparator.comparing(ApplicationDomainEvent::getCreatedAt))
        .toList();
  }
}
