package uk.gov.justice.laa.dstew.access.spike.dynamo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.spike.Event;
import uk.gov.justice.laa.dstew.access.spike.EventHistoryPublisher;

@Slf4j
@RequiredArgsConstructor
@Service
public class EventHistoryOutboxPattern {

  private final DomainEventRepository domainEventRepository;
  private final EventHistoryPublisher eventHistoryPublisher;
  private final DomainEventService domainEventService;

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
        .map(eventHistoryPublisher::processDomainEventAsync)
        .map(future -> future.thenApply(optUuid -> optUuid.orElse(null)))
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
        .whenCompleteAsync(
            (unused, throwable) -> handlePublicationResult(saveTasks, throwable, startTime, dynamoStartTime, uploadTime));
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

    updatePublishedStatusForBatch(publishedEventIds, startTime, uploadTime, dynamoTime, saveTasks.size());
  }

  /**
   * Updates the published status for a batch of events and logs summary statistics.
   * Only called by the batch processing operation.
   */
  private void updatePublishedStatusForBatch(List<UUID> publishedEventIds, long startTime,
                                             long uploadTime, long dynamoTime, int totalTasks) {
    long dbUpdateStart = System.currentTimeMillis();
    int updated = domainEventService.updateEventsPublishedStatus(publishedEventIds);
    long dbUpdateTime = System.currentTimeMillis() - dbUpdateStart;
    long totalTime = System.currentTimeMillis() - startTime;

    log.info("========== Event Publication Summary ==========");
    log.info("Events processed: {}", updated);
    log.info("S3 upload time: {} ms ({} uploads/second)",
        uploadTime,
        String.format("%.2f", totalTasks / (uploadTime / 1000.0)));
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
        .map(Event::convertToEvent)
        .limit(eventsToProcess)
        .toList();
  }

}
