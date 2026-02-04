package uk.gov.justice.laa.dstew.access.spike;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;

/**
 * Test-only controller to verify DynamoDB writes end-to-end.
 *
 * <p>This is intended for local development checks with LocalStack.
 */
@RestController
@RequestMapping("/test/dynamo")
public class TestDynamoEventController {

  private final DynamoDbService dynamoDbService;
  private final S3UploadService s3UploadService;
  private final EventHistoryPublisher eventHistoryPublisher;

  public TestDynamoEventController(DynamoDbService dynamoDbService, S3UploadService s3UploadService,
                                   EventHistoryPublisher eventHistoryPublisher) {
    this.dynamoDbService = dynamoDbService;
    this.s3UploadService = s3UploadService;
    this.eventHistoryPublisher = eventHistoryPublisher;
  }

  @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public void saveEvent(@RequestBody Event event) {
    S3UploadResult s3UploadResult = s3UploadService.upload(event, "laa-data-stewardship-access-bucket", event.applicationId());
    CompletableFuture<Event> eventCompletableFuture = dynamoDbService.saveDomainEvent(event, s3UploadResult.getS3Url());
    eventCompletableFuture.whenComplete((event1, throwable) -> {
        if (throwable != null) {
            System.err.println("Failed to save event: " + throwable.getMessage());
        } else {
            System.out.println("Event saved successfully: " + event1);

        }
    });
  }

  @GetMapping(value = "bucket/download", produces = MediaType.APPLICATION_JSON_VALUE)
  public S3DownloadResult downloadEventFromS3(@RequestParam String bucket, @RequestParam String key) {
    return s3UploadService.download(bucket, key);
  }

  @GetMapping(value = "dynamo/events", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<DomainEventDynamoDB> getAllEvents() {
    return dynamoDbService.getAllEvents();

  }

  @GetMapping(value = "dynamo/events/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Event> getEvents(@PathVariable String id,
                               @RequestParam (required = false) List<DomainEventType> eventTypes, @RequestParam (required = false) String caseworkerId) {
    if(caseworkerId != null) {
      return dynamoDbService.getDomainEventDynamoDBForCasework(id, caseworkerId, eventTypes != null ? eventTypes
          .stream()
              .findFirst().orElse(null) : null
          );
    }

    if (eventTypes != null) {
      return dynamoDbService.getAllApplicationsByIdAndEventType(id, eventTypes).stream().map(Event::fromDynamoEntity).toList();
    }
    return dynamoDbService.getAllApplicationsById(id).stream().map(Event::fromDynamoEntity).toList();

  }

  /**
   * Publishes a specified number of events to DynamoDB.
   * @param publishAmount the number of events to publish
   */
  @GetMapping(value = "publish/{publishAmount}", produces = MediaType.APPLICATION_JSON_VALUE)
  public void publishEvents(@PathVariable (required = false) Integer publishAmount ) {
    int amountToPublish = publishAmount == null ? 10 : publishAmount;
    eventHistoryPublisher.processDomainEvents(amountToPublish);
  }

}

