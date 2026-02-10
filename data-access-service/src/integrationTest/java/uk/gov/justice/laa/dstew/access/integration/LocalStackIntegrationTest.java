package uk.gov.justice.laa.dstew.access.integration;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.util.Map;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.config.devlopment.LocalStackResourceInitializer;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.LocalstackContainerInitializer;


import uk.gov.justice.laa.dstew.access.utils.LocalStackTestUtility;

@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalStackIntegrationTest extends BaseIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(LocalStackIntegrationTest.class);

//  @Autowired
//  private DynamoDbClient dynamoDbClient;
//
//  @Autowired
//  private S3Client s3Client;

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
