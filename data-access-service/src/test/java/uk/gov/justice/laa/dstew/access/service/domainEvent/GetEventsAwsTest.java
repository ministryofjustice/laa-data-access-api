package uk.gov.justice.laa.dstew.access.service.domainEvent;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;


@TestPropertySource(properties = "event.history.service.type=aws")
public class GetEventsAwsTest extends BaseServiceTest {

  @Autowired
  private EventHistoryService serviceUnderTest;

  @Test
  void givenExpectedDomainEvents_whenGetEvents_thenReturnDomainEventsInCreatedAtOrder() {

    UUID eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    // given
    setSecurityContext(TestConstants.Roles.READER);
    List<DomainEventEntity> generatedDomainEvents = Collections.singletonList(DataGenerator.createDefault(
        DomainEventGenerator.class, e -> e.applicationId(eventId)));

//    List<DomainEventEntity> orderedExpectedDomainEvents = generatedDomainEvents.stream()
//        .sorted(Comparator.comparing(DomainEventEntity::getCreatedAt))
//        .toList();
    List<Map<String, AttributeValue>> responseAsListAttrributeMap = getResponseAsListAttrributeMap(generatedDomainEvents);
    QueryResponse mockResponse = QueryResponse.builder()
        .items(responseAsListAttrributeMap)
        .build();
    when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);
    when(eventTable.tableName()).thenReturn("EventIndexTable");
//    when(domainEventRepository.findAll(any(Specification.class))).thenReturn(expectedDomainEvents);

    // when
    List<ApplicationDomainEvent> actualDomainEvents = serviceUnderTest.getEvents(
        UUID.randomUUID(),
        List.of(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
    );

    // then
    verify(domainEventRepository, never()).findAll(any(Specification.class));
    verify(dynamoDbClient).query(any(QueryRequest.class));
    assertDomainEventsEqual(generatedDomainEvents, actualDomainEvents);
  }

  @Test
  public void givenNotRoleReader_whenGetEvents_thenThrowUnauthorizedException() {
    // given
    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.getEvents(
            null,
            null
        ))
        .withMessageContaining("Access Denied");
    verify(domainEventRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenNoRole_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
    // given
    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.getEvents(
            null,
            null
        ))
        .withMessageContaining("Access Denied");
    verify(applicationSummaryRepository, never()).findAll();
  }

  private void assertDomainEventsEqual(List<DomainEventEntity> expected, List<ApplicationDomainEvent> actual) {
    assertThat(actual.size()).isEqualTo(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      assertDomainEventEqual(Event.fromEvent(Event.convertToEvent(expected.get(i))), actual.get(i));
    }
  }

  private void assertDomainEventEqual(ApplicationDomainEvent expected, ApplicationDomainEvent actual) {
    assertThat(expected.getApplicationId()).isEqualTo(actual.getApplicationId());
    assertThat(expected.getCaseworkerId()).isEqualTo(actual.getCaseworkerId());
    assertThat(expected.getDomainEventType().name()).isEqualTo(actual.getDomainEventType().name());
    assertThat(expected.getEventDescription()).isEqualTo(actual.getEventDescription());
    assertThat(expected.getCreatedAt()).isEqualTo(actual.getCreatedAt());
    assertThat(expected.getCreatedBy()).isEqualTo(actual.getCreatedBy());
  }

  private static @NonNull List<Map<String, AttributeValue>> getResponseAsListAttrributeMap(
      List<DomainEventEntity> generatedDomainEvents) {
    return generatedDomainEvents.stream().map(GetEventsAwsTest::convertToMap).toList();
  }

  private static Map<String, AttributeValue> convertToMap(DomainEventEntity entity) {
    Event convertToEvent = Event.convertToEvent(entity);
    DomainEventDynamoDb eventDynamoDb =
        getDomainEventDynamoDb(convertToEvent, "s3url", convertToEvent.applicationId(), convertToEvent.timestamp());
    return Map.of(
        "pk", AttributeValue.fromS(eventDynamoDb.getPk()),
        "sk", AttributeValue.fromS(eventDynamoDb.getSk()),
        "createdAt", AttributeValue.fromS(eventDynamoDb.getCreatedAt()),
        "description", AttributeValue.fromS(eventDynamoDb.getDescription()),
        "applicationId", AttributeValue.fromS(eventDynamoDb.getApplicationId()),
        "caseworkerId", AttributeValue.fromS(eventDynamoDb.getCaseworkerId()),
        "type", AttributeValue.fromS(eventDynamoDb.getType())
    );
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
}
