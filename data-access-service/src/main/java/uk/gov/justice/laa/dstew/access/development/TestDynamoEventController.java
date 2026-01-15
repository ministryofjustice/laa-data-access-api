package uk.gov.justice.laa.dstew.access.development;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.model.S3UploadResult;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;
import uk.gov.justice.laa.dstew.access.service.EventHistoryPublisher;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.service.S3Service;

/**
 * Test-only controller to verify DynamoDB writes end-to-end.
 *
 * <p>This is intended for local development checks with LocalStack.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/test/dynamo")
public class TestDynamoEventController {

  private final DynamoDbService dynamoDbService;
  private final S3Service s3Service;
  private final EventHistoryService eventHistoryService;
  private final EventHistoryPublisher eventHistoryPublisher;

  @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public void saveEvent(@RequestBody Event event) {
    S3UploadResult s3UploadResult = s3Service.upload(event, "laa-data-stewardship-access-bucket", event.applicationId());
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
  public String downloadEventFromS3(@RequestParam String bucket, @RequestParam String key) {
    if (bucket == null) {
      bucket = "laa-data-stewardship-access-bucket";
    }
    return s3Service.downloadObjectAsString(bucket, key);
  }


  @GetMapping(value = "dynamo/events/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Event> getEvents(@PathVariable String id,
                               @RequestParam(required = false) List<DomainEventType> eventTypes,
                               @RequestParam(required = false) String caseworkerId) {
    if (caseworkerId != null) {
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

  @GetMapping(value = "s3/application/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Map<String, Object>> getS3UrlForApplication(@PathVariable String applicationId) {
    return eventHistoryPublisher.getEventHistoryForApplication(applicationId);
  }

}

