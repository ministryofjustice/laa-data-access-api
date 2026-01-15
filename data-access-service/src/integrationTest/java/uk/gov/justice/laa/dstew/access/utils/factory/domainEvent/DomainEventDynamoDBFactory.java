package uk.gov.justice.laa.dstew.access.utils.factory.domainEvent;

import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDB;
import uk.gov.justice.laa.dstew.access.utils.factory.DynamoDbFactory;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventDynamoDBGenerator;

import java.util.function.Consumer;

public class DomainEventDynamoDBFactory implements DynamoDbFactory<DomainEventDynamoDB, DomainEventDynamoDB.DomainEventDynamoDBBuilder> {
    @Override
    public DomainEventDynamoDB create() {
        return DataGenerator.createDefault(DomainEventDynamoDBGenerator.class);
    }

    @Override
    public DomainEventDynamoDB create(Consumer<DomainEventDynamoDB.DomainEventDynamoDBBuilder> customiser) {
        return DataGenerator.createDefault(DomainEventDynamoDBGenerator.class, customiser);
    }
}
