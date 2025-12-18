package uk.gov.justice.laa.dstew.access.utils.factory.domainEvent;

import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

import java.util.UUID;
import java.util.function.Consumer;

public class DomainEventFactory {
    public static DomainEventEntity create() {

        DomainEventEntity entity = DomainEventEntity.builder()
                .applicationId(UUID.randomUUID())
                .caseWorkerId(UUID.randomUUID())
                .createdBy("")
                .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                .data("")
                .build();

        return entity;
    }

    public static DomainEventEntity create(Consumer<DomainEventEntity.DomainEventEntityBuilder> customiser) {
        DomainEventEntity entity = create();
        DomainEventEntity.DomainEventEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}
