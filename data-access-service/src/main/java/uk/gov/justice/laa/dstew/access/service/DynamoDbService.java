package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedResponse;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ReturnItemCollectionMetrics;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.utilities.DynamoKeyBuilder;

/**
 * Service class for interacting with DynamoDB.
 */
@Slf4j
@Service
public class DynamoDbService {

  public static final String APPLICATION = "APPLICATION";
  public static final String GS_INDEX_1 = "gs-index-1";
  private final String tableName;
  private final DynamoDbClient dynamoDbClient;
  private final DynamoDbTable<DomainEventDynamoDb> eventTable;
  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

  /**
   * Constructor for DynamoDbService.
   *
   * @param dynamoDbClient         the DynamoDbClient to use for low-level operations
   * @param dynamoDbEnhancedClient the DynamoDbEnhancedClient to use for high-level operations
   * @param eventTable             the DynamoDbTable to use for event operations
   */
  public DynamoDbService(DynamoDbClient dynamoDbClient,
                         DynamoDbEnhancedClient dynamoDbEnhancedClient,
                         DynamoDbTable<DomainEventDynamoDb> eventTable) {
    this.tableName = eventTable.tableName();
    this.dynamoDbClient = dynamoDbClient;
    this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
    this.eventTable = eventTable;
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
      if (eventRecord.applicationId() == null && eventRecord.timestamp() == null) {
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("eventRecord must have an applicationId and timestamp"));
      }
      String eventId = eventRecord.applicationId();
      Instant timestamp = eventRecord.timestamp();

      DomainEventDynamoDb domainEvent =
          getDomainEventDynamoDb(eventRecord, s3url, eventId, timestamp);

      // Use putItem variant that returns a PutItemEnhancedResponse so callers can inspect metadata
      PutItemEnhancedRequest<DomainEventDynamoDb> putReq = PutItemEnhancedRequest.builder(DomainEventDynamoDb.class)
          .item(domainEvent)
          .returnValues(ReturnValue.ALL_OLD)
          .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
          .returnItemCollectionMetrics(ReturnItemCollectionMetrics.SIZE)
          .build();

      PutItemEnhancedResponse<DomainEventDynamoDb> enhancedResponse =
          eventTable.putItemWithResponse(putReq);
      log.info("Consumed Capacity {}", enhancedResponse.consumedCapacity());
      return CompletableFuture.completedFuture(Event.fromDynamoEntity(domainEvent));
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private static DomainEventDynamoDb getDomainEventDynamoDb(Event eventRecord, String s3url, String eventId,
                                                            Instant timestamp) {
    return DomainEventDynamoDb.builder()
        .applicationId(eventId)
        .type(eventRecord.eventType().toString())
        .s3location(s3url)
        .description(eventRecord.description())
        .createdAt(timestamp.toString())
        .caseworkerId(eventRecord.caseworkerId())
        .build();
  }

  /**
   * Retrieves all DomainEventDynamoDB items for a given application ID.
   *
   * @param id the application ID to query for
   * @return a list of DomainEventDynamoDB items associated with the specified application ID
   */
  public List<DomainEventDynamoDb> getAllApplicationsById(String id) {
    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(eventTable.tableName())
        .keyConditionExpression("pk = :uid")
        .expressionAttributeValues(Map.of(
            ":uid", AttributeValue.fromS(DynamoKeyBuilder.pk(APPLICATION, id))
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDb.class)
        .mapToItem(item)
    ).toList();
  }

  /**
   * Retrieves all DomainEventDynamoDB items for a given application ID that were created before a specified time.
   *
   * @param id        the application ID to query for
   * @param untilTime the cutoff time; only items created before this time will be returned
   * @return a list of DomainEventDynamoDB items associated with the specified application ID and created before the cutoff time
   */
  public List<DomainEventDynamoDb> getAllApplicationsByIdUntilTime(String id, Instant untilTime) {
    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(tableName)
        .indexName(GS_INDEX_1)
        .keyConditionExpression("gs1pk = :gs1pkVal AND begins_with(gs1sk, :gs1skVal)")
        .expressionAttributeValues(Map.of(
            ":gs2pkVal", AttributeValue.fromS(DynamoKeyBuilder.pk(APPLICATION, id)),
            ":gs2skVal", AttributeValue.fromS(untilTime.toString())
        )).build();
    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDb.class)
        .mapToItem(item)
    ).toList();
  }

  /**
   * Retrieves all DomainEventDynamoDB items for a given application ID and a list of event types.
   *
   * @param id        the application ID to query for
   * @param eventType the list of event types to filter by; only items with these event types will be returned
   * @return a list of DomainEventDynamoDB items associated with the specified application ID and event types
   */
  public List<DomainEventDynamoDb> getAllApplicationsByIdAndEventType(String id,
                                                                      List<DomainEventType> eventType) {
    return eventType.stream()
        .map(et -> getDomainEventList(id, et))
        .flatMap(List::stream)
        .toList();
  }

  private @NonNull List<DomainEventDynamoDb> getDomainEventList(String id, DomainEventType eventType) {
    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(eventTable.tableName())
        .keyConditionExpression("pk = :uid AND begins_with(sk, :etype)")
        .expressionAttributeValues(Map.of(
            ":uid", AttributeValue.fromS(DynamoKeyBuilder.pk(APPLICATION, id)),
            ":etype", AttributeValue.fromS(eventType.name())
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);

    return response.items().stream().map(item -> TableSchema.fromBean(DomainEventDynamoDb.class)
        .mapToItem(item)
    ).toList();
  }

  /**
   * Retrieves a list of Event objects for a given application ID, caseworker ID, and event type.
   * This method queries a DynamoDB Global Secondary Index (GSI)
   * to find events associated with the specified caseworker and application.
   *
   * @param id           the application ID to query for
   * @param caseworkerId the caseworker ID to query for
   * @param eventType    the event type to filter by; only events of this type will be returned
   * @return a list of Event objects that match the specified criteria
   */
  public List<Event> getDomainEventForCaseworker(String id, String caseworkerId, DomainEventType eventType) {
    String pk = DynamoKeyBuilder.pk(APPLICATION, id);

    QueryRequest queryRequest = QueryRequest.builder()
        .tableName(tableName)
        .indexName(GS_INDEX_1)
        .keyConditionExpression("gs1pk = :gs1pkVal AND begins_with(gs1sk, :gs1skVal)")
        .expressionAttributeValues(Map.of(
            ":gs1pkVal", AttributeValue.fromS("CASEWORKER#" + caseworkerId),
            ":gs1skVal", AttributeValue.fromS(pk)
        )).build();

    QueryResponse response = dynamoDbClient.query(queryRequest);
    // If index table is not full copy then will need look up for main table to get full details

    List<Key> indexedEvents = getIndexedEvents(eventType, response);

    if (indexedEvents.isEmpty()) {
      return List.of();
    }

    return getEventsFromIndex(indexedEvents);
  }

  private @NonNull List<Event> getEventsFromIndex(List<Key> indexedEvents) {
    final int batchSize = 100;
    return getBatches(indexedEvents, batchSize).stream()
        .map(this::addBatchResultToEvent).flatMap(List::stream).toList();

  }

  private List<Event> addBatchResultToEvent(List<Key> batch) {
    BatchProcessResult initialResult = batchGetWithUnprocessedKeys(batch);
    List<Event> events = new ArrayList<>(initialResult.events());
    List<Key> unprocessedKeys = new ArrayList<>(initialResult.unprocessedKeys());

    final int maxRetries = 3;
    int attempt = 0;
    while (!unprocessedKeys.isEmpty() && attempt < maxRetries) {
      attempt++;
      BatchProcessResult retryResult = batchGetWithUnprocessedKeys(unprocessedKeys);
      events.addAll(retryResult.events());
      unprocessedKeys = new ArrayList<>(retryResult.unprocessedKeys());
    }

    if (!unprocessedKeys.isEmpty()) {
      log.warn("Unprocessed keys remain after {} retries: {}", maxRetries, unprocessedKeys.size());
    }

    return events;
  }

  private BatchProcessResult batchGetWithUnprocessedKeys(List<Key> keys) {
    ReadBatch.Builder<DomainEventDynamoDb> readBatchBuilder = ReadBatch.builder(DomainEventDynamoDb.class)
        .mappedTableResource(eventTable);
    keys.forEach(readBatchBuilder::addGetItem);
    BatchGetItemEnhancedRequest batchGetItemEnhancedRequest = BatchGetItemEnhancedRequest.builder()
        .addReadBatch(readBatchBuilder.build())
        .build();
    BatchGetResultPageIterable batchResult = dynamoDbEnhancedClient
        .batchGetItem(batchGetItemEnhancedRequest);

    List<Key> unprocessedKeys = batchResult.stream()
        .map(result -> result.unprocessedKeysForTable(eventTable))
        .flatMap(List::stream)
        .toList();

    List<Event> events = batchResult.resultsForTable(eventTable).stream()
        .map(Event::fromDynamoEntity)
        .toList();

    return new BatchProcessResult(events, unprocessedKeys);
  }

  private record BatchProcessResult(List<Event> events, List<Key> unprocessedKeys) {
  }

  private @NonNull List<Key> getIndexedEvents(DomainEventType eventType, QueryResponse response) {
    return response.items().stream()
        .map(this::convertToIndexedEvent)
        .filter(e -> e.eventType().equals(eventType))
        .map(indexedEvent -> Key
            .builder()
            .partitionValue(indexedEvent.pk)
            .sortValue(indexedEvent.sk)
            .build())
        .toList();
  }

  private static @NonNull List<List<Key>> getBatches(List<Key> indexedEvents, int batchSize) {
    AtomicInteger index = new AtomicInteger();
    return indexedEvents.stream()
        .collect(java.util.stream.Collectors.groupingBy(key -> index.getAndIncrement() / batchSize))
        .values().stream().toList();
  }

  private IndexedEvent convertToIndexedEvent(Map<String, AttributeValue> item) {
    String pk = item.get("pk").s();
    String sk = item.get("sk").s();
    DomainEventType eventType = DomainEventType.valueOf(item.get("type").s());
    return new IndexedEvent(pk, sk, eventType);
  }

  record IndexedEvent(String pk, String sk, DomainEventType eventType) {
  }


}
