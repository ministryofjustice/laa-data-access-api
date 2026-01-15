package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedResponse;
import uk.gov.justice.laa.dstew.access.entity.dynamo.EventDynamoEntity;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.shared.dynamo.DynamoKeyBuilder;

/**
 * Service class for interacting with DynamoDB.
 */
@Service
public class DynamoDbService {

  private final DynamoDbTable<EventDynamoEntity> eventTable;

  public DynamoDbService(
      DynamoDbEnhancedClient dynamoDbEnhancedClient,
      @Value("${aws.dynamodb.table-name:EventIndexTable}") String tableName
  ) {
    this.eventTable = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(EventDynamoEntity.class));
  }

  /**
   * Stores an Event in DynamoDB and returns the saved event.
   */
  public Event saveDomainEvent(Event eventRecord, String s3url) {
    if (eventRecord == null) {
      throw new IllegalArgumentException("eventRecord must not be null");
    }

    String eventId = eventRecord.eventId() != null ? eventRecord.eventId() : UUID.randomUUID().toString();
    Instant timestamp = eventRecord.timestamp() != null ? eventRecord.timestamp() : Instant.now();

    EventDynamoEntity eventDynamoEntity = EventDynamoEntity.builder()
        .pk(DynamoKeyBuilder.pk(eventRecord.eventType(), eventId))
        .sk(DynamoKeyBuilder.sk(timestamp))
        .s3location(s3url)
        .type(eventRecord.eventType())
        .description(eventRecord.description())
        .createdAt(DynamoKeyBuilder.sk(timestamp))
        .build();

    // Use putItem variant that returns a PutItemEnhancedResponse so callers can inspect metadata
    PutItemEnhancedRequest<EventDynamoEntity> putReq = PutItemEnhancedRequest.builder(EventDynamoEntity.class)
        .item(eventDynamoEntity)
        .build();

    PutItemEnhancedResponse<EventDynamoEntity> putResponse = eventTable.putItemWithResponse(putReq);
    return Event.fromDynamoEntity(putResponse.attributes());
  }
}
