package uk.gov.justice.laa.dstew.access.spike;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventHistoryPublisher {

  private final S3Service s3Service;
  private final DynamoDbService dynamoDbService;

  public EventHistoryPublisher(S3Service s3Service, DynamoDbService dynamoDbService) {
    this.s3Service = s3Service;
    this.dynamoDbService = dynamoDbService;
  }

  /**
   * Processes a single domain event asynchronously: uploads to S3 and saves to DynamoDB.
   * Does NOT update the published status - caller is responsible for that if needed.
   *
   * @param event the event to process
   * @return a CompletableFuture containing the event's UUID if successful, or empty if failed
   */
  public CompletableFuture<Optional<UUID>> processDomainEventAsync(Event event) {
    return processEventAsync(event)
        .thenApply(Optional::ofNullable);
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
   * Uploads the event data to S3 and returns the S3 URL if successful.
   *
   * @param event the event to upload
   * @return the S3 URL or null if the upload failed
   */
  private @Nullable String uploadedS3Url(Event event) {
    S3UploadResult s3UploadResult =
        s3Service.upload(event.requestPayload(), "laa-data-stewardship-access-bucket",
            event.applicationId() + "/" + event.domainEventId());
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


}
