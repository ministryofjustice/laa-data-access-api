package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;

/**
 * Service class for interacting with DynamoDB.
 */
@Service
public class DynamoDbService {

  public static final String APPLICATION = "APPLICATION";
  public static final String CASEWORKER = "APPLICATION";
  private final String tableName;
  private final DynamoDbTable<DomainEventDynamoDB> eventTable;
  private final DynamoDbClient dynamoDbClient;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  public DynamoDbService(@Value("${aws.dynamodb.table-name:EventIndexTable}") String tableName, DynamoDbClient dynamoDbClient, DynamoDbEnhancedClient dynamoDbEnhancedClient) {
    this.tableName = tableName;
    this.dynamoDbClient = dynamoDbClient;
    this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
    eventTable = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(DomainEventDynamoDB.class));
  }

  /**
   * Asynchronously stores an Event in DynamoDB and returns a CompletableFuture with the saved event.
   */
  @Async
  public CompletableFuture<Event> saveDomainEvent(Event eventRecord, String s3url) {
    if (eventRecord == null) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("eventRecord must not be null"));
    }

    try {
      String eventId = eventRecord.eventId() != null ? eventRecord.eventId() : UUID.randomUUID().toString();
      Instant timestamp = eventRecord.timestamp() != null ? eventRecord.timestamp() : Instant.now();

      DomainEventDynamoDB domainEventDynamoDB = DomainEventDynamoDB.builder()
          .pk(DynamoKeyBuilder.pk(APPLICATION, eventId))
          .sk(DynamoKeyBuilder.sk(eventRecord.eventType(), timestamp))
          .s3location(s3url)
          .type(eventRecord.eventType().toString())
          .description(eventRecord.description())
          .createdAt(timestamp.toString())
          .caseworkerId(eventRecord.caseworkerId())
          .build();

      // Use putItem variant that returns a PutItemEnhancedResponse so callers can inspect metadata
      PutItemEnhancedRequest<DomainEventDynamoDB> putReq = PutItemEnhancedRequest.builder(DomainEventDynamoDB.class)
          .item(domainEventDynamoDB)
          .build();

      PutItemEnhancedResponse<DomainEventDynamoDB> putResponse = eventTable.putItemWithResponse(putReq);
      return CompletableFuture.completedFuture(Event.fromDynamoEntity(putResponse.attributes()));
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public long countEvents() {
    return eventTable.scan().items().stream().count();
  }

  public List<DomainEventDynamoDB> getAllEvents() {
    return eventTable.scan().items().stream().toList();
  }

  public List<DomainEventDynamoDB> getAllApplicationsById(String id) {
    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(eventTable.tableName())
        .keyConditionExpression("pk = :uid")
        .expressionAttributeValues(Map.of(
            ":uid", AttributeValue.fromS(DynamoKeyBuilder.pk(APPLICATION, id))
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDB.class)
        .mapToItem(item)
    ).toList();
  }

  public List<DomainEventDynamoDB> getAllApplicationsByIdAndEventType(String id, List<EventType> eventType) {
    return eventType.stream()
        .map(et -> getDomainEventDynamoDBList(id, et))
        .flatMap(List::stream)
        .toList();
  }

  private @NonNull List<DomainEventDynamoDB> getDomainEventDynamoDBList(String id, EventType eventType) {
    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(eventTable.tableName())
        .keyConditionExpression("pk = :uid AND begins_with(sk, :etype)")
        .expressionAttributeValues(Map.of(
            ":uid", AttributeValue.fromS(DynamoKeyBuilder.pk(APPLICATION, id)),
            ":etype", AttributeValue.fromS(eventType.name())
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDB.class)
        .mapToItem(item)
    ).toList();
  }

  public List<DomainEventDynamoDB> getDomainEventDynamoDBForCasework(String id, String caseworkerId, EventType eventType) {
    String pk = DynamoKeyBuilder.pk(APPLICATION, id);

    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(tableName)
        .indexName("gs-index-1")
        .keyConditionExpression("gs1pk = :gs1pkVal AND begins_with(gs1sk, :gs1skVal)")
        .expressionAttributeValues(Map.of(
            ":gs1pkVal", AttributeValue.fromS("CASEWORKER#" + caseworkerId),
            ":gs1skVal", AttributeValue.fromS(pk)
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDB.class)
        .mapToItem(item)
    ).toList();
  }
  
}
