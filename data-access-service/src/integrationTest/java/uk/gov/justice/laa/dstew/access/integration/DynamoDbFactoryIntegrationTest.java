package uk.gov.justice.laa.dstew.access.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDB;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.LocalStackTestUtility;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedDynamoDbFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.domainEvent.DomainEventDynamoDBFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamoDbFactoryIntegrationTest extends BaseIntegrationTest {

    private static final TableSchema<DomainEventDynamoDB> TABLE_SCHEMA = TableSchema.fromBean(DomainEventDynamoDB.class);
  private LocalStackTestUtility localStackTestUtility;

  @BeforeEach
  void initializeResources() {
    localStackTestUtility = new LocalStackTestUtility();
    localStackTestUtility.createTableWithGsi(dynamoDbClient);
    localStackTestUtility.createBucket(s3Client);
  }


    @Test
    void testCreateAndPersistDomainEvent() {
        PersistedDynamoDbFactory<DomainEventDynamoDBFactory, DomainEventDynamoDB, DomainEventDynamoDB.DomainEventDynamoDBBuilder> persistedFactory =
                new PersistedDynamoDbFactory<>(dynamoDbEnhancedClient, new DomainEventDynamoDBFactory(), DomainEventDynamoDB.class, "events");

        DomainEventDynamoDB event = persistedFactory.createAndPersist(builder -> builder.description("Custom description"));

        DomainEventDynamoDB retrievedEvent = dynamoDbEnhancedClient.table("events", TABLE_SCHEMA)
                .getItem(r -> r.key(k -> k.partitionValue(event.getPk()).sortValue(event.getSk())));

        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getDescription()).isEqualTo("Custom description");
    }
}
