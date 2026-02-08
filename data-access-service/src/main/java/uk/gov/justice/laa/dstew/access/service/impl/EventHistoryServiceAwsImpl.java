package uk.gov.justice.laa.dstew.access.service.impl;

import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.spike.DynamoDbService;
import uk.gov.justice.laa.dstew.access.spike.Event;

@RequiredArgsConstructor
@Service
public class EventHistoryServiceAwsImpl implements EventHistoryService {
  private final DynamoDbService dynamoDbService;

  /**
   * Provides a list of events associated with an application in createdAt ascending order.
   */
  @Override
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
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
