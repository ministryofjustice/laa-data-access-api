package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
                .items(List.of(
                        Map.of("pk", AttributeValue.builder().s("APPLICATION#123").build(),
                                "sk", AttributeValue.builder().s("2024-01-01T12:00:00Z").build(),
                                "type", AttributeValue.builder().s(DomainEventType.APPLICATION_CREATED.getValue()).build())
                ))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);
        when(eventTable.tableName()).thenReturn("domain-events");

        List<DomainEventDynamoDb> result = dynamoDbService.getAllApplicationsById(testId);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllApplicationsByIdUntilTime() {
        String testId = "123";
        Instant testUntilTime = Instant.now();
        QueryResponse mockResponse = QueryResponse.builder()
                .items(List.of(
                        Map.of("pk", AttributeValue.builder().s("APPLICATION#123").build(),
                                "sk", AttributeValue.builder().s("2024-01-01T12:00:00Z").build(),
                                "type", AttributeValue.builder().s(DomainEventType.APPLICATION_CREATED.getValue()).build())
                ))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        List<DomainEventDynamoDb> result = dynamoDbService.getAllApplicationsByIdUntilTime(testId, testUntilTime);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllApplicationsByIdAndEventType() {
        String testId = "123";
        QueryResponse mockResponse = QueryResponse.builder()
                .items(List.of(
                        Map.of("pk", AttributeValue.builder().s("APPLICATION#123").build(),
                                "sk", AttributeValue.builder().s("2024-01-01T12:00:00Z").build(),
                                "type", AttributeValue.builder().s(DomainEventType.APPLICATION_CREATED.getValue()).build())
                ))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);
        when(eventTable.tableName()).thenReturn("EventIndexTable");

        List<DomainEventDynamoDb> result = dynamoDbService.getAllApplicationsByIdAndEventType(testId, List.of(DomainEventType.APPLICATION_CREATED));

        assertThat(result).hasSize(1);
    }

    @Test
    void getDomainEventForCaseworker() {
        String testId = "123";
        String testCaseworkerId = "cwid";
        QueryResponse mockQueryResponse = QueryResponse.builder()
                .items(List.of(
                        Map.of("pk", AttributeValue.builder().s("APPLICATION#123").build(),
                                "sk", AttributeValue.builder().s("APPLICATION_CREATED#2024-01-01T12:00:00Z").build(),
                                "type", AttributeValue.builder().s(DomainEventType.APPLICATION_CREATED.getValue()).build())
                ))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockQueryResponse);

        DomainEventDynamoDb domainEvent = DomainEventDynamoDb.builder()
                .applicationId("123")
                .type(DomainEventType.APPLICATION_CREATED.getValue())
                .createdAt("2024-01-01T12:00:00Z")
                .build();


//        var mockBatchResponse = BatchGetItemEnhancedRequest.builder().build();
//        var page = BatchGetResultPage.builder(DomainEventDynamoDb.class)
//                .results(List.of(domainEvent))
//                .build();
//        var mockIterable = PageIterable.create(List.of(page));
//
//        when(dynamoDbEnhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).thenReturn(mockIterable);
//        when(eventTable.tableSchema()).thenReturn(TableSchema.fromBean(DomainEventDynamoDb.class));
//
        List<Event> result = dynamoDbService.getDomainEventForCaseworker(testId, testCaseworkerId, DomainEventType.APPLICATION_CREATED);

        assertThat(result).hasSize(1);
    }
}