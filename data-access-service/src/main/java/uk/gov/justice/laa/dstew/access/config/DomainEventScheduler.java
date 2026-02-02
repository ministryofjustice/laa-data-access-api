package uk.gov.justice.laa.dstew.access.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.spike.DynamoDbService;
import uk.gov.justice.laa.dstew.access.spike.Event;
import uk.gov.justice.laa.dstew.access.spike.S3UploadResult;
import uk.gov.justice.laa.dstew.access.spike.S3UploadService;

@Slf4j
@RequiredArgsConstructor
@Component
public class DomainEventScheduler {

private final DomainEventService domainEventService;
private final S3UploadService s3UploadService;
private final DynamoDbService dynamoDbService;
private final DomainEventRepository domainEventRepository;


    @Scheduled(fixedDelay = 30000)
    public void processDomainEvents() {
      log.info("Processing all domain events");
      List<Event> unpublisedEvents = domainEventRepository.
          findAllByIsPublishedFalse()
          .stream()
          .map(this::convertToEvent)
          .limit(10)
          .toList();
      log.info("Found {} unpublished events", unpublisedEvents.size());
      List<UUID> publishedEventIds = new ArrayList<>();

      for (Event event : unpublisedEvents) {
          S3UploadResult s3UploadResult =
              s3UploadService.upload(event, "laa-data-stewardship-access-bucket", event.applicationId());
          log.info("S3 Upload success:" + s3UploadResult.isSuccess());
          String s3Url = s3UploadResult.getS3Url();
            log.info("Uploading domain event with id '{}'", event.applicationId());

            dynamoDbService.saveDomainEvent(event, s3Url)
                .thenAccept(savedEvent -> {

                  log.info("Domain event with id '{}' saved successfully", event.applicationId());
                  publishedEventIds.add(event.domainEventId());
//                    domainEventRepository.setIsPublishedTrueForEventId(savedEvent.domainEventId());
                })
                .exceptionally(ex -> {
                    // Handle exception (e.g., log the error)
                    log.error("Failed to process event with ID {}: {}", event.domainEventId(), ex.getMessage());
                    return null;
                });
        }
        // After processing all events, update their status in the database
      int updated = domainEventService.updateEventsPublishedStatus(publishedEventIds);
        log.info("Updated {} events as published", updated);
    }

    private Event convertToEvent(DomainEventEntity domainEventEntity) {
        return Event.builder()
            .eventType(domainEventEntity.getType())
            .applicationId(String.valueOf(domainEventEntity.getApplicationId()))
            .caseworkerId(String.valueOf(domainEventEntity.getCaseworkerId()))
            .timestamp(domainEventEntity.getCreatedAt())
            .description(domainEventEntity.getData().substring(0, 10))
            .domainEventId(domainEventEntity.getId())
            .build();

    }
}
