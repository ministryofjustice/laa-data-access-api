package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

@SpringBootTest(classes = DynamoDbService.class)
class DynamoDbServiceTest {

  @MockitoBean
  private DynamoDbClient dynamoDbClient;

  @MockitoBean
  private DynamoDbEnhancedClient dynamoDbEnhancedClient;

  @MockitoBean
  private DynamoDbTable<DomainEventDynamoDb> eventTable;

  @Autowired
  private DynamoDbService dynamoDbService;


  @Test
  void saveDomainEvent() throws ExecutionException, InterruptedException {
    DomainEventEntity domainEventEntity = DataGenerator.createDefault(DomainEventGenerator.class);
    Event testEvent = Event.convertToEvent(domainEventEntity);
    String testS3Url = "s3://url";

    DomainEventDynamoDb domainEventDynamoDb = DomainEventDynamoDb.builder()
        .applicationId(testEvent.applicationId())
        .type(testEvent.eventType().getValue())
        .s3location(testS3Url)
        .build();
    when(eventTable.putItemWithResponse(any(PutItemEnhancedRequest.class)))
        .thenReturn(PutItemEnhancedResponse.builder(DomainEventDynamoDb.class).attributes(domainEventDynamoDb).build());

    CompletableFuture<Event> result = dynamoDbService.saveDomainEvent(testEvent, testS3Url);
    Event savedEvent = result.get();

    assertNotNull(savedEvent);
    assertEquals(testEvent.applicationId(), savedEvent.applicationId());
  }

  @Test
  void getAllApplicationsById() {
    String testId = "123";
    QueryResponse mockResponse = QueryResponse.builder()
        .items(getResponseAsListAttrributeMap())
        .build();
    when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);
    when(eventTable.tableName()).thenReturn("domain-events");

    List<DomainEventDynamoDb> result = dynamoDbService.getAllApplicationsById(testId);

    assertThat(result).hasSize(1);
  }

  private static @NonNull List<Map<String, AttributeValue>> getResponseAsListAttrributeMap() {
    return List.of(
        Map.of("pk", AttributeValue.fromS("APPLICATION#123"),
            "sk", AttributeValue.fromS("2024-01-01T12:00:00Z"),
            "createdAt", AttributeValue.fromS("2024-01-01T12:00:00Z"),
            "description", AttributeValue.fromS("test description"),
            "type", AttributeValue.fromS(DomainEventType.APPLICATION_CREATED.getValue()))
    );
  }

  @Test
  void getAllApplicationsByIdUntilTime() {
    String testId = "123";
    Instant testUntilTime = Instant.now();
    QueryResponse mockResponse = QueryResponse.builder()
        .items(getResponseAsListAttrributeMap())
        .build();
    when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

    List<DomainEventDynamoDb> result = dynamoDbService.getAllApplicationsByIdUntilTime(testId, testUntilTime);

    assertThat(result).hasSize(1);
  }

  @Test
  void getAllApplicationsByIdAndEventType() {
    String testId = "123";
    QueryResponse mockResponse = QueryResponse.builder()
        .items(getResponseAsListAttrributeMap())
        .build();
    when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);
    when(eventTable.tableName()).thenReturn("EventIndexTable");

    List<DomainEventDynamoDb> result =
        dynamoDbService.getAllApplicationsByIdAndEventType(testId, List.of(DomainEventType.APPLICATION_CREATED));

    assertThat(result).hasSize(1);
  }

  @Test
  void getDomainEventForCaseworker() {
    when(eventTable.tableName()).thenReturn("domain-events");
    String testId = "123";
    String testCaseworkerId = "cwid";
    QueryResponse mockQueryResponse = QueryResponse.builder()
        .items(getResponseAsListAttrributeMap())
        .build();
    when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockQueryResponse);

    BatchGetItemResponse batchGetItemResponse = BatchGetItemResponse.builder()
        .responses(Map.of("domain-events", getResponseAsListAttrributeMap())
        ).build();
    BatchGetResultPage build = BatchGetResultPage.builder().batchGetItemResponse(batchGetItemResponse).build();
    List<BatchGetResultPage> batchGetResultPageList = new ArrayList<>();
    batchGetResultPageList.add(build);

    SdkIterable<BatchGetResultPage> sdkIterable = new BatchGetResultPageIterable() {
      @Override
      public @NonNull Iterator<BatchGetResultPage> iterator() {
        return batchGetResultPageList.iterator();
      }
    };
    BatchGetResultPageIterable batchGetResultPages = BatchGetResultPageIterable.create(
        sdkIterable);

    when(dynamoDbEnhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
        .thenReturn(batchGetResultPages);
    when(eventTable.tableSchema()).thenReturn(TableSchema.fromBean(DomainEventDynamoDb.class));
    List<Event> result =
        dynamoDbService.getDomainEventForCaseworker(testId, testCaseworkerId, DomainEventType.APPLICATION_CREATED);

    assertThat(result).hasSize(1);
  }
}