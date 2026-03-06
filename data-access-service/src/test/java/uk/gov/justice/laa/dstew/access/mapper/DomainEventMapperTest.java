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
    @ValueSource(strings = { "377adde8-632f-43c6-b10b-0843433759d3" })
    void givenDomainEntity_whenToDomainEvent_thenMapsFieldsCorrectly(String caseworkerIdStr) {
        UUID id = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = caseworkerIdStr != null ? UUID.fromString(caseworkerIdStr) : null;
        Instant createdAt = Instant.ofEpochMilli(999_999_000);
        OffsetDateTime expectedCreatedDateTime = OffsetDateTime.of(1970, 1, 12, 13, 46, 39, 0, ZoneOffset.UTC);
        String createdBy = "John.Doe";
        String eventDescription = "{ \"eventDescription\" : \"eventDescription\" }";
        DomainEventType eventType = DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER;

        DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                .id(id)
                .applicationId(applicationId)
                .caseworkerId(caseworkerId)
                .createdAt(createdAt)
                .createdBy(createdBy)
                .data(eventDescription)
                .type(eventType)
                .build();

        ApplicationDomainEvent actualDomainEvent = mapper.toDomainEvent(domainEventEntity);

        assertThat(actualDomainEvent.getApplicationId()).isEqualTo(applicationId);
        assertThat(actualDomainEvent.getCaseworkerId()).isEqualTo(caseworkerId);
        assertThat(actualDomainEvent.getCreatedAt()).isEqualTo(expectedCreatedDateTime);
        assertThat(actualDomainEvent.getCreatedBy()).isEqualTo(createdBy);
        assertThat(actualDomainEvent.getDomainEventType()).isEqualTo(eventType);
        assertThat(actualDomainEvent.getEventDescription()).isEqualTo(eventDescription);
    }
}