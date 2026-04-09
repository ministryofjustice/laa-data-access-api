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
    String eventDescription = "{ \"eventDescription\" : \"eventDescription\" }";
    DomainEventType eventType = DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER;

    DomainEventEntity entity =
        DomainEventEntity.builder()
            .id(id)
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .createdAt(createdAt)
            .createdBy(createdBy)
            .data(eventDescription)
            .type(eventType)
            .build();

    ApplicationDomainEventResponse result = mapper.toDomainEvent(entity);

    assertThat(result.getApplicationId()).isEqualTo(applicationId);
    assertThat(result.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(result.getCreatedAt()).isEqualTo(expectedCreatedDateTime);
    assertThat(result.getCreatedBy()).isEqualTo(createdBy);
    assertThat(result.getDomainEventType()).isEqualTo(eventType);
    assertThat(result.getEventDescription()).isEqualTo(eventDescription);
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
}
