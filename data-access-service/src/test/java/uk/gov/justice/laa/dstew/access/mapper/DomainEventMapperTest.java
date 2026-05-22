package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

@ExtendWith(MockitoExtension.class)
public class DomainEventMapperTest extends BaseMapperTest {

  @InjectMocks private DomainEventMapperImpl mapper;

  @Test
  void givenNullEntity_whenToDomainEvent_thenReturnNull() {
    assertThat(mapper.toDomainEvent(null)).isNull();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"377adde8-632f-43c6-b10b-0843433759d3"})
  void givenDomainEntity_whenToDomainEvent_thenMapsFieldsCorrectly(String caseworkerIdStr) {
    UUID id = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = caseworkerIdStr != null ? UUID.fromString(caseworkerIdStr) : null;
    Instant createdAt = Instant.ofEpochMilli(999_999_000);
    OffsetDateTime expectedCreatedDateTime =
        OffsetDateTime.of(1970, 1, 12, 13, 46, 39, 0, ZoneOffset.UTC);
    String createdBy = "John.Doe";
    String expectedDescription = "test event description";
    String dataJson = "{ \"eventDescription\" : \"" + expectedDescription + "\" }";
    DomainEventType eventType = DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER;

    DomainEventEntity entity =
        DomainEventEntity.builder()
            .id(id)
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .createdAt(createdAt)
            .createdBy(createdBy)
            .data(dataJson)
            .type(eventType)
            .build();

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getApplicationId()).isEqualTo(applicationId);
    assertThat(result.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(result.getCreatedAt()).isEqualTo(expectedCreatedDateTime);
    assertThat(result.getCreatedBy()).isEqualTo(createdBy);
    assertThat(result.getDomainEventType()).isEqualTo(eventType);
    assertThat(result.getEventDescription()).isEqualTo(expectedDescription);
  }

  @Test
  void givenEntityWithAllNullFields_whenToDomainEvent_thenAllFieldsAreNull() {
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .applicationId(null)
                    .caseworkerId(null)
                    .createdBy(null)
                    .data(null)
                    .type(null));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getApplicationId()).isNull();
    assertThat(result.getCaseworkerId()).isNull();
    assertThat(result.getCreatedBy()).isNull();
    assertThat(result.getEventDescription()).isNull();
    assertThat(result.getDomainEventType()).isNull();
  }

  @Test
  void givenDataJsonWithNoEventDescriptionKey_whenToDomainEvent_thenEventDescriptionIsNull() {
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("{\"otherField\": \"someValue\"}")
                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isNull();
  }

  @Test
  void givenMalformedDataJson_whenToDomainEvent_thenEventDescriptionIsNull() {
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("not-valid-json")
                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isNull();
  }

  @Test
  void givenDataJsonWithNullEventDescriptionValue_whenToDomainEvent_thenEventDescriptionIsNull() {
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("{\"eventDescription\": null}")
                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isNull();
  }

  @Test
  void givenEventTypeWithNoEventDescription_whenToDomainEvent_thenEventDescriptionIsNull() {
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("{\"applicationId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}")
                    .type(DomainEventType.APPLICATION_CREATED));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isNull();
  }

  @Test
  void givenUnassignEventTypeWithEventDescription_whenToDomainEvent_thenEventDescriptionReturned() {
    String expectedDescription = "Unassigned from caseworker";
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("{\"eventDescription\": \"" + expectedDescription + "\"}")
                    .type(DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isEqualTo(expectedDescription);
  }

  @Test
  void
      givenMakeDecisionEventTypeWithEventDescription_whenToDomainEvent_thenEventDescriptionReturned() {
    String expectedDescription = "Decision granted";
    DomainEventEntity entity =
        DataGenerator.createDefault(
            DomainEventGenerator.class,
            builder ->
                builder
                    .data("{\"eventDescription\": \"" + expectedDescription + "\"}")
                    .type(DomainEventType.APPLICATION_MAKE_DECISION_GRANTED));

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getEventDescription()).isEqualTo(expectedDescription);
  }
}
