package uk.gov.justice.laa.dstew.access.controller;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;

/**
 * Test-only controller to verify DynamoDB writes end-to-end.
 *
 * <p>This is intended for local development checks with LocalStack.
 */
@RestController
@RequestMapping("/test/dynamo")
public class TestDynamoEventController {

  private final DynamoDbService dynamoDbService;

  public TestDynamoEventController(DynamoDbService dynamoDbService) {
    this.dynamoDbService = dynamoDbService;
  }

  @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Event saveEvent(@RequestBody SaveEventRequest request) {
    // allow callers to omit id/timestamp
    Event event = new Event(
        request.eventType(),
        request.eventId() != null ? request.eventId() : UUID.randomUUID(),
        request.timestamp() != null ? request.timestamp() : Instant.now(),
        request.description());

    return dynamoDbService.saveDomainEvent(event);
  }

  public record SaveEventRequest(String eventType, UUID eventId, Instant timestamp, String description) {
  }
}

