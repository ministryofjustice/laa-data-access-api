package uk.gov.justice.laa.dstew.access.spike;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;

@Slf4j
@RequiredArgsConstructor
@Component
public class EventHistoryPublisher {

  private final DomainEventService domainEventService;
  private final S3UploadService s3UploadService;
  private final DynamoDbService dynamoDbService;
  private final DomainEventRepository domainEventRepository;

  /**
   * Processes all unpublished domain events by uploading them to S3 and saving them to DynamoDB.
   *
   * @param eventsToProcess the maximum number of events to process
   */
  public void processDomainEvents(int eventsToProcess) {
    log.info("Processing up to {} domain events", eventsToProcess);

    List<CompletableFuture<UUID>> saveTasks = getUnpublishedEvents(eventsToProcess).stream()
        .map(this::createSaveTask)
        .flatMap(Optional::stream)
        .toList();

    if (saveTasks.isEmpty()) {
      log.info("No domain events ready for publication");
      return;
    }

    CompletableFuture.allOf(saveTasks.toArray(new CompletableFuture[0]))
        .whenCompleteAsync((unused, throwable) -> handlePublicationResult(saveTasks, throwable));
  }

  private void handlePublicationResult(List<CompletableFuture<UUID>> saveTasks, Throwable throwable) {
    if (throwable != null) {
      log.error("One or more domain events failed during publication", throwable);
    }

    List<UUID> publishedEventIds = saveTasks.stream()
        .map(this::awaitCompletion)
        .flatMap(Optional::stream)
        .toList();

    if (publishedEventIds.isEmpty()) {
      log.warn("Domain events were uploaded but none saved successfully to DynamoDB");
      return;
    }

    int updated = domainEventService.updateEventsPublishedStatus(publishedEventIds);
    log.info("Updated {} events as published", updated);
  }

  private Optional<CompletableFuture<UUID>> createSaveTask(Event event) {
    return Optional.ofNullable(uploadedS3Url(event))
        .map(s3Url -> getSaveTask(event, s3Url));
  }

  /**
   * Awaits the completion of a CompletableFuture and handles exceptions.
   *
   * @param future the CompletableFuture to await
   * @return an Optional containing the result if successful, or empty if an exception occurred
   */
  private Optional<UUID> awaitCompletion(CompletableFuture<UUID> future) {
    try {
      return Optional.ofNullable(future.join());
    } catch (CompletionException ex) {
      log.error("Failed to persist domain event", ex);
      return Optional.empty();
    }
  }

  /**
   * Creates a task to save the event to DynamoDB.
   *
   * @param event the event to save
   * @param s3Url the S3 URL of the uploaded event data
   * @return a CompletableFuture representing the save task
   */
  private @NonNull CompletableFuture<UUID> getSaveTask(Event event, String s3Url) {
    return dynamoDbService.saveDomainEvent(event, s3Url)
        .thenApply(__ -> event.domainEventId())
        .whenComplete((__, throwable) -> {
          if (throwable == null) {
            log.info("Domain event with id '{}' saved successfully", event.applicationId());
          } else {
            log.error("Failed to process event with ID {}", event.domainEventId(), throwable);
          }
        });
  }

  /**
   * Uploads the event data to S3 and returns the S3 URL if successful.
   *
   * @param event the event to upload
   * @return the S3 URL or null if the upload failed
   */
  private @Nullable String uploadedS3Url(Event event) {
    S3UploadResult s3UploadResult =
        s3UploadService.upload(event, "laa-data-stewardship-access-bucket", event.applicationId());
    if (!s3UploadResult.isSuccess()) {
      log.error("S3 Upload failed for event id '{}'", event.domainEventId());
      return null;
    }
    log.info("S3 Upload success: {}", s3UploadResult.isSuccess());
    String s3Url = s3UploadResult.getS3Url();
    log.info("Uploading domain event with id '{}'", event.applicationId());
    if (s3Url == null) {
      log.error("S3 URL is null for event id '{}'", event.domainEventId());
    }
    return s3Url;
  }

  /**
   * Fetches unpublished events from the repository and converts them to Event objects.
   * Temporarily using DomainEvent until EventHistory is implemented.
   *
   * @param eventsToProcess the maximum number of events to process
   * @return a list of unpublished Event objects
   */
  private @NonNull List<Event> getUnpublishedEvents(int eventsToProcess) {
    return domainEventRepository.
        findAllByIsPublishedFalse()
        .stream()
        .map(this::convertToEvent)
        .limit(eventsToProcess)
        .toList();
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
