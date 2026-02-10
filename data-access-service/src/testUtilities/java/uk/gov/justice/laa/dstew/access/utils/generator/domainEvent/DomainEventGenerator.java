package uk.gov.justice.laa.dstew.access.utils.generator.domainEvent;

import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.time.Instant;
import java.util.UUID;

public class DomainEventGenerator extends BaseGenerator<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> {
    public DomainEventGenerator() {
        super(DomainEventEntity::toBuilder, DomainEventEntity.DomainEventEntityBuilder::build);
    }

    @Override
    public DomainEventEntity createDefault() {
        return DomainEventEntity.builder()
                .applicationId(UUID.randomUUID())
                .caseworkerId(UUID.randomUUID())
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data("")
                .build();
    }
}
