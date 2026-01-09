package uk.gov.justice.laa.dstew.access.utils.factory.domainEvent;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Profile("unit-test")
@Component
public class DomainEventFactory extends BaseFactory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> {

    public DomainEventFactory() {
        super(DomainEventEntity::toBuilder, DomainEventEntity.DomainEventEntityBuilder::build);
    }

    public DomainEventEntity createDefault() {

        DomainEventEntity entity = DomainEventEntity.builder()
                .applicationId(UUID.randomUUID())
                .caseworkerId(UUID.randomUUID())
                .createdAt(Instant.now())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data("")
                .build();

        return entity;
    }
}
