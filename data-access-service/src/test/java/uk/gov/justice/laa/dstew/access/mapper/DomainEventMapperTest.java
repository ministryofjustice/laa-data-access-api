package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

public class DomainEventMapperTest {

    private final DomainEventMapper mapper = Mappers.getMapper(DomainEventMapper.class);

    @ParameterizedTest
    @NullSource
    @ValueSource( strings = { "377adde8-632f-43c6-b10b-0843433759d3" })
    void givenDomainEntity_whenToDomainEvent_thenMapsFieldsCorrectly(String caseworkerId) {
        final String eventDescription = "{ \"eventDescription\" : \"eventDescription\" }";
        Instant createdAt = Instant.ofEpochMilli(999999000);
        OffsetDateTime expectedCreatedDateTime = OffsetDateTime.of(1970, 01, 12, 13, 46, 39, 0, ZoneOffset.UTC);
        DomainEventEntity expectedDomainEventEntity = DomainEventEntity.builder()
                                                    .id(UUID.randomUUID())
                                                    .applicationId(UUID.randomUUID())
                                                    .caseworkerId(caseworkerId != null ? UUID.fromString(caseworkerId) : null)
                                                    .createdAt(createdAt)
                                                    .createdBy("John.Doe")
                                                    .data(eventDescription)
                                                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                                                    .build();
        ApplicationDomainEvent actualDomainEvent = mapper.toDomainEvent(expectedDomainEventEntity);
        
        assertThat(actualDomainEvent.getApplicationId()).isEqualTo(expectedDomainEventEntity.getApplicationId());
        assertThat(actualDomainEvent.getCaseworkerId()).isEqualTo(expectedDomainEventEntity.getCaseworkerId());
        assertThat(actualDomainEvent.getCreatedAt()).isEqualTo(expectedCreatedDateTime);
        assertThat(actualDomainEvent.getCreatedBy()).isEqualTo(expectedDomainEventEntity.getCreatedBy());
        assertThat(actualDomainEvent.getDomainEventType()).isEqualTo(expectedDomainEventEntity.getType());
        assertThat(actualDomainEvent.getEventDescription()).isEqualTo(eventDescription);
    }
}
