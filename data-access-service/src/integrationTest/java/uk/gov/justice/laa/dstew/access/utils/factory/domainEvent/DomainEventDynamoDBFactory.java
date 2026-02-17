package uk.gov.justice.laa.dstew.access.utils.factory.domainEvent;

import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.utils.factory.DynamoDbFactory;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventDynamoDbGenerator;

import java.util.function.Consumer;

public class DomainEventDynamoDBFactory implements DynamoDbFactory<DomainEventDynamoDb, DomainEventDynamoDb.DomainEventDynamoDbBuilder> {
    @Override
    public DomainEventDynamoDb create() {
        return DataGenerator.createDefault(DomainEventDynamoDbGenerator.class);
    }

    @Override
    public DomainEventDynamoDb create(Consumer<DomainEventDynamoDb.DomainEventDynamoDbBuilder> customiser) {
        return DataGenerator.createDefault(DomainEventDynamoDbGenerator.class, customiser);
    }
}
