package uk.gov.justice.laa.dstew.access.utils.generator.domainEvent;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/**
 * Generator for creating DomainEventDynamoDB instances with default values for testing purposes.
 */
public class DomainEventDynamoDbGenerator
    extends BaseGenerator<DomainEventDynamoDb, DomainEventDynamoDb.DomainEventDynamoDbBuilder> {
  public DomainEventDynamoDbGenerator() {
    super(DomainEventDynamoDb::toBuilder, DomainEventDynamoDb.DomainEventDynamoDbBuilder::build);
  }

  @Override
  public DomainEventDynamoDb createDefault() {
    return DomainEventDynamoDb.builder()
        .applicationId(UUID.randomUUID().toString())
        .type(DomainEventType.APPLICATION_CREATED.name())
        .s3location("s3://test-bucket/test-file.json")
        .description("Test event")
        .createdAt(Instant.now().toString())
        .caseworkerId(UUID.randomUUID().toString())
        .build();
  }

}
