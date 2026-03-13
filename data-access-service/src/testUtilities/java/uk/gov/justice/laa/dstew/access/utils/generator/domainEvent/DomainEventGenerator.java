package uk.gov.justice.laa.dstew.access.utils.generator.domainEvent;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class DomainEventGenerator extends BaseGenerator<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> {
    public DomainEventGenerator() {
        super(DomainEventEntity::toBuilder, DomainEventEntity.DomainEventEntityBuilder::build);
    }

    @Override
    public DomainEventEntity createDefault() {

        return DomainEventEntity.builder()
                .applicationId(UUID.randomUUID())
                .caseworkerId(UUID.randomUUID())
            .createdAt(Instant.parse("2024-01-01T12:00:00Z"))
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data("{\"eventDescription\": \"" + DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER.getValue() + "\"}")
                .build();
    }
}
