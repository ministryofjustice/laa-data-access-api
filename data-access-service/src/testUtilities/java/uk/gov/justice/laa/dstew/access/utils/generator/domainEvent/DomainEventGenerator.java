package uk.gov.justice.laa.dstew.access.utils.generator.domainEvent;

import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class DomainEventGenerator extends BaseGenerator<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> {
    public DomainEventGenerator() {
        super(DomainEventEntity::toBuilder, DomainEventEntity.DomainEventEntityBuilder::build);
    }

    @Override
    public DomainEventEntity createDefault() {
        return DomainEventEntity.builder()
                .applicationId(java.util.UUID.randomUUID())
                //.caseworkerId(java.util.UUID.randomUUID())
                .createdAt(java.time.Instant.now())
                .createdBy("")
                .type(uk.gov.justice.laa.dstew.access.model.DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data("")
                .build();
    }
}
