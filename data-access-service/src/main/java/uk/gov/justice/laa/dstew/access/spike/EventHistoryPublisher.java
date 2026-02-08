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
  private final S3Service s3Service;
  private final DynamoDbService dynamoDbService;
  private final DomainEventRepository domainEventRepository;

  /**
   * Processes a single domain event asynchronously: uploads to S3, saves to DynamoDB, and updates published status.
   *
   * @param event the event to process
   * @return a CompletableFuture containing the event's UUID if successful, or empty if failed
   */
  public CompletableFuture<Optional<UUID>> processDomainEventAsync(Event event) {
    return processEventAsync(event)
        .thenApply(uuid -> {
          if (uuid != null) {
            int updated = domainEventService.updateEventsPublishedStatus(List.of(uuid));
            if (updated == 1) {
              log.info("Event {} published and status updated", uuid);
              return Optional.of(uuid);
            } else {
              log.warn("Event {} published but status update failed", uuid);
            }
          }
          return Optional.empty();
        });
  }

  /**
   * Core async processing for a single event: upload to S3 and save to DynamoDB.
   * Returns the event UUID if successful, null otherwise.
   */
  private CompletableFuture<UUID> processEventAsync(Event event) {
    return CompletableFuture.supplyAsync(() -> uploadedS3Url(event))
        .thenCompose(s3Url -> {
          if (s3Url == null) {
            return CompletableFuture.completedFuture(null);
          }
          return dynamoDbService.saveDomainEvent(event, s3Url)
              .thenApply(__ -> event.domainEventId())
              .whenComplete((__, throwable) -> {
                if (throwable == null) {
                  log.info("Domain event with id '{}' saved successfully", event.applicationId());
                } else {
                  log.error("Failed to process event with ID {}", event.domainEventId(), throwable);
                }
              });
        });
  }

  /**
   * Processes all unpublished domain events by uploading them to S3 and saving them to DynamoDB.
   * Uses parallel processing for improved performance.
   *
   * @param eventsToProcess the maximum number of events to process
   */
  public void processDomainEvents(int eventsToProcess) {
    log.info("Processing up to {} domain events", eventsToProcess);
    long startTime = System.currentTimeMillis();

    List<Event> events = getUnpublishedEvents(eventsToProcess);
    List<CompletableFuture<UUID>> saveTasks = events.stream()
        .parallel()
        .map(this::processEventAsync)
        .toList();

    if (saveTasks.isEmpty()) {
      log.info("No domain events ready for publication");
      return;
    }

    long uploadTime = System.currentTimeMillis() - startTime;
    log.info("S3 uploads completed for {} events in {} ms ({} uploads/second)",
        saveTasks.size(),
        uploadTime,
        String.format("%.2f", saveTasks.size() / (uploadTime / 1000.0)));

    long dynamoStartTime = System.currentTimeMillis();
    CompletableFuture.allOf(saveTasks.toArray(new CompletableFuture[0]))
        .whenCompleteAsync((unused, throwable) -> handlePublicationResult(saveTasks, throwable, startTime, dynamoStartTime, uploadTime));
  }

  private void handlePublicationResult(List<CompletableFuture<UUID>> saveTasks, Throwable throwable,
                                       long startTime, long dynamoStartTime, long uploadTime) {
    if (throwable != null) {
      log.error("One or more domain events failed during publication", throwable);
    }

    List<UUID> publishedEventIds = saveTasks.stream()
        .parallel() // Parallelize awaiting completions
        .map(this::awaitCompletion)
        .flatMap(Optional::stream)
        .toList();

    if (publishedEventIds.isEmpty()) {
      log.warn("Domain events were uploaded but none saved successfully to DynamoDB");
      return;
    }

    long dynamoTime = System.currentTimeMillis() - dynamoStartTime;
    log.info("DynamoDB saves completed for {} events in {} ms ({} saves/second)",
        publishedEventIds.size(),
        dynamoTime,
        String.format("%.2f", publishedEventIds.size() / (dynamoTime / 1000.0)));

    long dbUpdateStart = System.currentTimeMillis();
    int updated = domainEventService.updateEventsPublishedStatus(publishedEventIds);
    long dbUpdateTime = System.currentTimeMillis() - dbUpdateStart;
    long totalTime = System.currentTimeMillis() - startTime;

    log.info("========== Event Publication Summary ==========");
    log.info("Events processed: {}", updated);
    log.info("S3 upload time: {} ms ({} uploads/second)",
        uploadTime,
        String.format("%.2f", saveTasks.size() / (uploadTime / 1000.0)));
    log.info("DynamoDB save time: {} ms ({} saves/second)",
        dynamoTime,
        String.format("%.2f", publishedEventIds.size() / (dynamoTime / 1000.0)));
    log.info("Database update time: {} ms", dbUpdateTime);
    log.info("Total execution time: {} ms ({} events/second)",
        totalTime,
        String.format("%.2f", updated / (totalTime / 1000.0)));
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
   * Uploads the event data to S3 and returns the S3 URL if successful.
   *
   * @param event the event to upload
   * @return the S3 URL or null if the upload failed
   */
  private @Nullable String uploadedS3Url(Event event) {
    S3UploadResult s3UploadResult =
        s3Service.upload(event.requestPayload(), "laa-data-stewardship-access-bucket", event.applicationId() + "/" + event.domainEventId());
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
        .description(domainEventEntity.getType().name() + " event for application " + domainEventEntity.getApplicationId())
        .requestPayload(domainEventEntity.getData())
        .domainEventId(domainEventEntity.getId())
        .build();

  }
}
