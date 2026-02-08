package uk.gov.justice.laa.dstew.access.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.MappedTableResource;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.spike.Event;
import uk.gov.justice.laa.dstew.access.spike.LocalStackResourceInitializer;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;
import uk.gov.justice.laa.dstew.access.config.devlopment.LocalStackResourceInitializer;

@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalStackIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(LocalStackIntegrationTest.class);
  private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.2.0");
  private static final Region AWS_REGION = Region.EU_WEST_2;

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(S3, DYNAMODB)
      .withEnv("DEFAULT_REGION", AWS_REGION.toString());

  private static S3Client s3Client;
  private static DynamoDbClient dynamoDbClient;
  private static DynamoDbEnhancedClient enhancedClient;

  @BeforeAll
  static void setUp() {
    log.info("Starting LocalStack container...");
    s3Client = S3Client.builder()
        .endpointOverride(localstack.getEndpointOverride(S3))
        .region(Region.of(localstack.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                localstack.getAccessKey(),
                localstack.getSecretKey()
            )
        )).build();
    dynamoDbClient = DynamoDbClient.builder()
        .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
        .region(Region.of(localstack.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                localstack.getAccessKey(),
                localstack.getSecretKey()
            )
        ))
        .build();
    enhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .build();

    log.info("LocalStack container started and clients configured.");
  }

  @BeforeEach
  void initializeResources() {
    createTableWithGsi();
    createBucket();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("cloud.aws.region.static", AWS_REGION::toString);
    registry.add("cloud.aws.credentials.access-key", () -> "test");
    registry.add("cloud.aws.credentials.secret-key", () -> "test");
    registry.add("cloud.aws.stack-name", () -> "localstack");
    registry.add("cloud.aws.endpoint-override", () -> localstack.getEndpointOverride(DYNAMODB).toString());
  }

  @AfterAll
  void tearDown() {
    // No need to stop LocalStack container, Testcontainers will handle it
  }

  private void createTableWithGsi() {
    String tableName = "events";
    if (dynamoDbClient.listTables().tableNames().contains(tableName)) {
      dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
    }

    CreateTableRequest request = LocalStackResourceInitializer
        .getCreateTableRequest(LocalStackResourceInitializer.getGlobalSecondaryIndex1(),
            LocalStackResourceInitializer.getGlobalSecondaryIndex2(), tableName);

    try {
      dynamoDbClient.createTable(request);
    } catch (ResourceInUseException e) {
      // Table already exists - make creation idempotent so tests can run against existing LocalStack
      // (for example when Testcontainers can't create containers and we fallback to an existing compose-managed LocalStack).
      // Continue to waiting for the table to be ACTIVE below.
    }

    // wait until ACTIVE
    waitForTableActive(tableName);
  }

  private void waitForTableActive(String tableName) {
    for (int i = 0; i < 30; i++) {
      try {
        DescribeTableResponse resp = dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
        if (resp.table().tableStatus() == TableStatus.ACTIVE) {
          return;
        }
        Thread.sleep(500);
      } catch (ResourceNotFoundException rnfe) {
        // continue waiting
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("Table did not become active in time");
  }

  private void createBucket() {
    String bucketName = "test-bucket";
    int attempts = 0;
    int maxAttempts = 30;
    while (true) {
      try {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        return;
      } catch (S3Exception s3e) {
        // If the bucket already exists, consider creation successful
        try {
          ListBucketsResponse list = s3Client.listBuckets();
          boolean exists = list.buckets().stream().anyMatch(b -> bucketName.equals(b.name()));
          if (exists) {
            return;
          }
        } catch (Exception ignored) {
          // ignore and fall through to retry
        }

        attempts++;
        if (attempts >= maxAttempts) {
          throw new RuntimeException("Failed to create S3 bucket after retries", s3e);
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
      } catch (Exception e) {
        // Non-S3 exceptions: attempt to detect if the bucket exists (fallback) then retry
        try {
          ListBucketsResponse list = s3Client.listBuckets();
          boolean exists = list.buckets().stream().anyMatch(b -> bucketName.equals(b.name()));
          if (exists) {
            return;
          }
        } catch (Exception ignored) {
          // ignore and fall through to retry
        }

        attempts++;
        if (attempts >= maxAttempts) {
          throw new RuntimeException("Failed to create S3 bucket after retries", e);
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
      }
    }
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
