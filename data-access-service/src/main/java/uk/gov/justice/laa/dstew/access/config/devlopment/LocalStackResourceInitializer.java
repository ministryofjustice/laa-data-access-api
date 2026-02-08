package uk.gov.justice.laa.dstew.access.config.devlopment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Component that initializes AWS resources (S3 bucket and DynamoDB table) when the application starts in the 'local' profile.
 * This is intended for development environments using LocalStack,
 * ensuring that necessary resources are available without manual setup.
 */
@Component
@Profile("localstack") // Only run this component when the 'localstack' profile is active
public class LocalStackResourceInitializer {

  private static final Logger log = LoggerFactory.getLogger(LocalStackResourceInitializer.class);

  private final S3Client s3Client;
  private final DynamoDbClient dynamoDbClient;
  private final String bucketName;
  private final String tableName;

  /**
   * Constructor for LocalStackResourceInitializer.
   * Dependencies are injected by Spring, including the S3 and DynamoDB clients,
   * and the bucket/table names from application properties.
   *
   * @param s3Client       the S3 client to interact with S3 resources.
   * @param dynamoDbClient the DynamoDB client to interact with DynamoDB resources.
   * @param bucketName     the name of the S3 bucket to ensure exists.
   * @param tableName      the name of the DynamoDB table to ensure exists.
   */
  public LocalStackResourceInitializer(
      S3Client s3Client,
      DynamoDbClient dynamoDbClient,
      @Value("${aws.s3.bucket-name}") String bucketName,
      @Value("${aws.dynamodb.table-name}") String tableName) {
    this.s3Client = s3Client;
    this.dynamoDbClient = dynamoDbClient;
    this.bucketName = bucketName;
    this.tableName = tableName;
  }

  /**
   * Method that runs after the application is ready.
   * It checks for the existence of the specified S3 bucket and DynamoDB table, creating them if they do not exist.
   * This ensures that the necessary AWS resources are available for development using LocalStack.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initializeResources() {
    log.info("Local profile active. Initializing AWS resources for LocalStack...");
    ensureS3BucketExists(bucketName, s3Client);
    ensureDynamoDbTableExists();
    log.info("LocalStack resource initialization complete.");
  }

  private static void ensureS3BucketExists(String bucketName1, S3Client s3Client1) {
    try {
      s3Client1.headBucket(HeadBucketRequest.builder().bucket(bucketName1).build());
      log.info("S3 bucket '{}' already exists.", bucketName1);
    } catch (NoSuchBucketException e) {
      log.info("S3 bucket '{}' not found. Creating...", bucketName1);
      s3Client1.createBucket(CreateBucketRequest.builder().bucket(bucketName1).build());
      log.info("S3 bucket '{}' created.", bucketName1);
    }
  }

  private void ensureDynamoDbTableExists() {
    try {
      dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      log.info("DynamoDB table '{}' already exists.", tableName);
    } catch (ResourceNotFoundException e) {
      log.info("DynamoDB table '{}' not found. Creating...", tableName);
      CreateTableRequest request =
          getCreateTableRequest(tableName);
      dynamoDbClient.createTable(request);
      log.info("DynamoDB table '{}' created.", tableName);
    }
  }

  /**
   * Helper method to construct a CreateTableRequest for DynamoDB with the specified global secondary indexes and table name.
   *
   * @param tableName the name of the DynamoDB table to create.
   * @return a CreateTableRequest configured with the necessary key schema.
   */
  public static CreateTableRequest getCreateTableRequest(String tableName) {
    return CreateTableRequest.builder()
        .tableName(tableName)
        .keySchema(
            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
        )
        .attributeDefinitions(
            AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName("gs1pk").attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName("gs1sk").attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName("gs2pk").attributeType(ScalarAttributeType.S).build(),
            AttributeDefinition.builder().attributeName("gs2sk").attributeType(ScalarAttributeType.S).build()
        )
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .globalSecondaryIndexes(
            getGlobalSecondaryIndex1(),
            getGlobalSecondaryIndex2()
        )
        .build();
  }

  private static GlobalSecondaryIndex getGlobalSecondaryIndex1() {
    return GlobalSecondaryIndex.builder()
        .indexName("gs-index-1")
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .keySchema(
            KeySchemaElement.builder().attributeName("gs1pk").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("gs1sk").keyType(KeyType.RANGE).build())
        .projection(projection -> projection.nonKeyAttributes("pk", "sk", "s3location")
            .projectionType("INCLUDE"))
        .build();
  }

  private static GlobalSecondaryIndex getGlobalSecondaryIndex2() {
    return GlobalSecondaryIndex.builder()
        .indexName("gs-index-2")
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .keySchema(
            KeySchemaElement.builder().attributeName("gs2pk").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("gs2sk").keyType(KeyType.RANGE).build())
        .projection(projection -> projection.nonKeyAttributes("pk", "sk", "s3location", "createdAt")
            .projectionType("INCLUDE"))
        .build();
  }
}
