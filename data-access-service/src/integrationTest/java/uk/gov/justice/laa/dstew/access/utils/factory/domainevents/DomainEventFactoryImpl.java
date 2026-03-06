package uk.gov.justice.laa.dstew.access.utils.factory.domainevents;

import java.time.Instant;
import java.util.function.Consumer;

import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity.DomainEventEntityBuilder;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class DomainEventFactoryImpl implements Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> {

    @Override
    public DomainEventEntity create() {
        return DomainEventEntity.builder()
        .createdAt(Instant.now())
        .createdBy("John.Doe")
        .data("{ \"eventDescription\" : \"assigning to a caseworker\"}")
        .build();
    }

    @Override
    public DomainEventEntity create(Consumer<DomainEventEntityBuilder> customiser) {
        DomainEventEntity.DomainEventEntityBuilder builder = create().toBuilder();
        customiser.accept(builder);
        return builder.build(); 
    }    
}