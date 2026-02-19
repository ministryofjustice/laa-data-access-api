package uk.gov.justice.laa.dstew.access.integration;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.LocalStackTestUtility;

@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalStackIntegrationTest extends BaseIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(LocalStackIntegrationTest.class);

  private LocalStackTestUtility localStackTestUtility;

  @BeforeEach
  void initializeResources() {
    localStackTestUtility = new LocalStackTestUtility();
    localStackTestUtility.createTableWithGsi(dynamoDbClient);
    localStackTestUtility.createBucket(s3Client);
  }


  @Test
  void dynamoDbCheckCanSaveAndRetrieveItem() {
    dynamoDbClient.putItem(PutItemRequest.builder()
        .tableName("events")
        .item(Map.of(
            "pk", AttributeValue.fromS("test-pk"),
            "sk", AttributeValue.fromS("test-sk")))
        .build());

    Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
        .tableName("events")
        .key(Map.of(
            "pk", AttributeValue.fromS("test-pk"),
            "sk", AttributeValue.fromS("test-sk")))
        .build()
    ).item();

    Assertions.assertEquals("test-pk", item.get("pk").s());
    Assertions.assertEquals("test-sk", item.get("sk").s());

  }


}
