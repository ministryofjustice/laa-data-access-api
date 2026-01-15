package uk.gov.justice.laa.dstew.access.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.gov.justice.laa.dstew.access.entity.dynamo.EventDynamoEntity;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalStackIntegrationTest {

  private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:2.2.0");
  private LocalStackContainer localstack;
  private DynamoDbClient dynamoClient;
  private DynamoDbEnhancedClient enhancedClient;
  private S3Client s3Client;
  private boolean usingTestcontainersLocalstack = false;

  @BeforeAll
  void setUp() {
    // If the developer indicates they are running existing services (USE_EXISTING_SERVICES=true)
    // or PROVIDES a LOCALSTACK_ENDPOINT, prefer connecting to that instead of starting containers.
    String useExisting = System.getenv().getOrDefault("USE_EXISTING_SERVICES", "false");
    String providedEndpoint = System.getenv().get("LOCALSTACK_ENDPOINT");

    if ("true".equalsIgnoreCase(useExisting) || providedEndpoint != null) {
      usingTestcontainersLocalstack = false;
      URI endpoint = providedEndpoint != null ? URI.create(providedEndpoint) : URI.create("http://localhost:4566");
      String regionStr = System.getenv().getOrDefault("DEFAULT_REGION", "eu-west-2");

      dynamoClient = DynamoDbClient.builder()
          .endpointOverride(endpoint)
          .region(Region.of(regionStr))
          .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "y")))
          .build();
    } else {
      // Try to start a LocalStack container using Testcontainers (requires Docker access)
      try {
        localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.S3)
            .waitingFor(Wait.forLogMessage(".*Ready\\..*\\n", 1).withStartupTimeout(Duration.ofMinutes(3)));
        localstack.start();

        usingTestcontainersLocalstack = true;

        dynamoClient = DynamoDbClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "y")))
            .build();
      } catch (IllegalStateException e) {
        // Fallback: connect to localhost:4566 if Testcontainers isn't available for some reason
        usingTestcontainersLocalstack = false;
        URI endpoint = URI.create(System.getenv().getOrDefault("LOCALSTACK_ENDPOINT", "http://localhost:4566"));
        String regionStr = System.getenv().getOrDefault("DEFAULT_REGION", "eu-west-2");

        dynamoClient = DynamoDbClient.builder()
            .endpointOverride(endpoint)
            .region(Region.of(regionStr))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "y")))
            .build();
      }
    }

    enhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoClient)
        .build();

    // Configure S3 client to use the Testcontainers endpoint when available, otherwise localhost:4566
    URI s3Endpoint = usingTestcontainersLocalstack
        ? localstack.getEndpointOverride(LocalStackContainer.Service.S3)
        : URI.create(System.getenv().getOrDefault("LOCALSTACK_ENDPOINT", "http://localhost:4566"));

    Region s3Region = usingTestcontainersLocalstack
        ? Region.of(localstack.getRegion())
        : Region.of(System.getenv().getOrDefault("DEFAULT_REGION", "eu-west-2"));

    s3Client = S3Client.builder()
        .endpointOverride(s3Endpoint)
        .region(s3Region)
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).checksumValidationEnabled(false).build())
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "y")))
        .build();

    createTableWithGsi();
    createBucket();
  }

  private void createTableWithGsi() {
        String tableName = "events";
        if(dynamoClient.listTables().tableNames().contains(tableName)) {
          dynamoClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        }
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("gsiPk").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
                )
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("caseworkerId-index")
                        .keySchema(KeySchemaElement.builder().attributeName("gsiPk").keyType(KeyType.HASH).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
                        .build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
                .build();

        try {
            dynamoClient.createTable(request);
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceInUseException e) {
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
        DescribeTableResponse resp = dynamoClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
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

  @AfterAll
  void tearDown() {
    if (usingTestcontainersLocalstack && localstack != null) {
      localstack.stop();
    }
  }

  @Test
  void dynamoGsiQueryAndS3SmokeTest() {
    DynamoDbTable<EventDynamoEntity> table = enhancedClient.table("events", TableSchema.fromBean(EventDynamoEntity.class));

    UUID eventId = UUID.randomUUID();
    Instant now = Instant.now();
    EventDynamoEntity e = EventDynamoEntity.builder()
        .pk("EVENT#" + eventId)
        .sk(now.toString())
        .type("TEST")
        .description("desc")
        .caseworkerId("cw-123")
        .build();

    table.putItem(e);

    // Query the GSI
    DynamoDbIndex<EventDynamoEntity> gsi = table.index("caseworkerId-index");
    QueryConditional cond = QueryConditional.keyEqualTo(k -> k.partitionValue("CASEWORKER#cw-123"));
    // collect items across pages returned by the enhanced client
    List<EventDynamoEntity> results = new ArrayList<>();
    for (Page<EventDynamoEntity> page : gsi.query(r -> r.queryConditional(cond))) {
      results.addAll(page.items());
    }

    assertThat(results).hasSize(1);
    EventDynamoEntity saved =
        results.stream().findFirst().orElseThrow(() -> new AssertionError("Expected at least one item in GSI query"));
    assertThat(saved.getPk()).isEqualTo(e.getPk());

    // S3 smoke test - put and get
    String bucket = "test-bucket";
    String key = "test/key.txt";
    byte[] payload = "hello".getBytes();
    s3Client.putObject(b -> b.bucket(bucket).key(key), software.amazon.awssdk.core.sync.RequestBody.fromBytes(payload));

    byte[] downloaded = s3Client.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
    assertThat(downloaded).isEqualTo(payload);
  }
}
