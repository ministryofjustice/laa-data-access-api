package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

public class DomainEventMapperTest {

    private DomainEventMapper mapper = new DomainEventMapperImpl();

    @ParameterizedTest
    @NullSource
    @ValueSource( strings = { "377adde8-632f-43c6-b10b-0843433759d3" })
    void shouldMapDomainEventEntityToDomainEvent(String caseworkerId) {
        final String eventDescription = "Assigning to a senior caseworker";
        Instant createdAt = Instant.ofEpochMilli(999999000);
        OffsetDateTime expectedCreatedDateTime = OffsetDateTime.of(1970, 01, 12, 13, 46, 39, 0, ZoneOffset.UTC);
        DomainEventEntity entity = DomainEventEntity.builder()
                                                    .id(UUID.randomUUID())
                                                    .applicationId(UUID.randomUUID())
                                                    .caseworkerId(caseworkerId != null ? UUID.fromString(caseworkerId) : null)
                                                    .createdAt(createdAt)
                                                    .createdBy("John.Doe")
                                                    .data(Map.of("eventDescription", eventDescription))
                                                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER_)
                                                    .build();
        DomainEvent result = mapper.toDomainEvent(entity);
        
        assertThat(result.getApplicationId()).isEqualTo(entity.getApplicationId());
        assertThat(result.getCaseworkerId()).isEqualTo(entity.getCaseworkerId());
        assertThat(result.getCreatedAt()).isEqualTo(expectedCreatedDateTime);
        assertThat(result.getCreatedBy()).isEqualTo(entity.getCreatedBy());
        assertThat(result.getDomainEventType()).isEqualTo(entity.getType());
        assertThat(result.getEventDescription()).isEqualTo(eventDescription);
    }

    void shouldMapNullToEventDescriptionWhenEventDescriptionMissing() {
        DomainEventEntity entity = DomainEventEntity.builder()
                                                    .id(UUID.randomUUID())
                                                    .applicationId(UUID.randomUUID())
                                                    .createdAt(Instant.now())
                                                    .createdBy("John.Doe")
                                                    .data(Map.of("foo", "bar"))
                                                    .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER_)
                                                    .build();
        DomainEvent result = mapper.toDomainEvent(entity);

        assertThat(result.getEventDescription()).isNull();
    }
}
