package uk.gov.justice.laa.dstew.access.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.LocalStackTestUtility;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedDynamoDbFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.domainEvent.DomainEventDynamoDBFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamoDbFactoryIntegrationTest extends BaseIntegrationTest {

    private static final TableSchema<DomainEventDynamoDb> TABLE_SCHEMA = TableSchema.fromBean(DomainEventDynamoDb.class);
  private LocalStackTestUtility localStackTestUtility;

  @BeforeEach
  void initializeResources() {
    localStackTestUtility = new LocalStackTestUtility();
    localStackTestUtility.createTableWithGsi(dynamoDbClient);
    localStackTestUtility.createBucket(s3Client);
  }


    @Test
    void testCreateAndPersistDomainEvent() {
        PersistedDynamoDbFactory<DomainEventDynamoDBFactory, DomainEventDynamoDb, DomainEventDynamoDb.DomainEventDynamoDbBuilder> persistedFactory =
                new PersistedDynamoDbFactory<>(dynamoDbEnhancedClient, new DomainEventDynamoDBFactory(), DomainEventDynamoDb.class, "events");

        DomainEventDynamoDb event = persistedFactory.createAndPersist(builder -> builder.description("Custom description"));

        DomainEventDynamoDb retrievedEvent = dynamoDbEnhancedClient.table("events", TABLE_SCHEMA)
                .getItem(r -> r.key(k -> k.partitionValue(event.getPk()).sortValue(event.getSk())));

        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getDescription()).isEqualTo("Custom description");
    }
}
