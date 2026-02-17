package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.model.S3UploadResult;

/**
 * Service responsible for processing domain events: uploading event data to S3 and saving event metadata to DynamoDB.
 * Also provides functionality to retrieve event history for a given application ID.
 */
@Slf4j
@Component
public class EventHistoryPublisher {

  private final S3Service s3Service;
  private final DynamoDbService dynamoDbService;
  private final Executor executor;
  private final S3Client s3Client;
  @Value("${aws.s3.bucket-name:laa-data-stewardship-access-bucket}")
  private String s3BucketName;

  /**
   * Constructor for EventHistoryPublisher, injecting necessary services and configurations.
   *
   * @param s3Service       the service responsible for S3 interactions
   * @param dynamoDbService the service responsible for DynamoDB interactions
   * @param executor        the executor for asynchronous processing
   * @param s3Client        the AWS S3 client for direct S3 operations
   */
  public EventHistoryPublisher(S3Service s3Service, DynamoDbService dynamoDbService,
                               @Qualifier("applicationTaskExecutor") Executor executor, S3Client s3Client) {
    this.s3Service = s3Service;
    this.dynamoDbService = dynamoDbService;
    this.executor = executor;
    this.s3Client = s3Client;
  }


  /**
   * Core async processing for a single event: upload to S3 and save to DynamoDB.
   * Returns the event UUID if successful, null otherwise.
   */
  public CompletableFuture<UUID> processEventAsync(Event event) {
    return CompletableFuture.supplyAsync(() -> uploadedS3Url(event), executor)
        .thenComposeAsync(s3Url -> {
          if (s3Url == null) {
            return CompletableFuture.completedFuture(null);
          }
          return dynamoDbService.saveDomainEvent(event, s3Url)
              .thenApply(e -> event.domainEventId())
              .whenComplete((uuid, throwable) -> {
                if (throwable == null) {
                  log.info("Domain event with id '{}' saved successfully", event.applicationId());
                } else {
                  log.error("Failed to process event with ID {}", event.domainEventId(), throwable);
                }
              });
        }, executor);
  }


  /**
   * Uploads the event data to S3 and returns the S3 URL if successful.
   *
   * @param event the event to upload
   * @return the S3 URL or null if the upload failed
   */
  private @Nullable String uploadedS3Url(Event event) {
    S3UploadResult s3UploadResult =
        s3Service.upload(event.requestPayload(), s3BucketName,
            event.applicationId() + "/" + event.eventType().name() + "-" + event.timestamp() + ".json");
    if (!s3UploadResult.isSuccess()) {
      log.error("S3 Upload failed for event id '{}'", event.domainEventId());
      return null;
    }
    String s3Url = s3UploadResult.getS3Url();
    log.info("Uploading domain event with id '{}'", event.applicationId());
    if (s3Url == null) {
      log.error("S3 URL is null for event id '{}'", event.domainEventId());
    }
    return s3Url;
  }

  /**
   * Retrieves the event history for a given application ID by listing S3 objects and downloading their content.
   *
   * @param applicationId the application ID to retrieve events for
   * @return a map of S3 object keys to their corresponding event data and S3 URLs
   */
  public Map<String, Map<String, Object>> getEventHistoryForApplication(String applicationId) {
    List<String> s3ObjectKeys = getS3ObjectKeysForEvent(Event.builder().applicationId(applicationId).build());
    Map<String, Map<String, Object>> eventHistory = new HashMap<>();
    for (String s3ObjectKey : s3ObjectKeys) {
      String s3Url = "s3://" + s3BucketName + "/" + s3ObjectKey;
      try {
        String eventData = s3Service.downloadEventsAsStrings(s3Url);

        ObjectMapper objectMapper = MapperUtil.getObjectMapper();
        Map<String, Object> map = objectMapper.readValue(eventData, Map.class);
        eventHistory.put(s3ObjectKey, Map.of("s3Url", s3Url, "eventData", map));
      } catch (Exception e) {
        log.error("Failed to download event data from S3 for key '{}'", s3ObjectKey, e);
      }
    }
    return eventHistory;
  }

  private List<String> getS3ObjectKeysForEvent(Event event) {
    String prefix = event.applicationId() + "/";
    ListObjectsV2Request request = ListObjectsV2Request.builder()
        .bucket(s3BucketName)
        .prefix(prefix)
        .build();
    ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
    return listObjectsV2Response.contents().stream()
        .map(S3Object::key)
        .toList();
  }


}
