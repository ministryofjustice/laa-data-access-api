package uk.gov.justice.laa.dstew.access.spike;

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

@Component
@Profile("local") // Only run this component when the 'local' profile is active
public class LocalStackResourceInitializer {

    private static final Logger log = LoggerFactory.getLogger(LocalStackResourceInitializer.class);

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final String bucketName;
    private final String tableName;

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

    @EventListener(ApplicationReadyEvent.class)
    public void initializeResources() {
        log.info("Local profile active. Initializing AWS resources for LocalStack...");
        ensureS3BucketExists(bucketName, s3Client);
        ensureDynamoDbTableExists();
        log.info("LocalStack resource initialization complete.");
    }

    public static void ensureS3BucketExists(String bucketName1, S3Client s3Client1) {
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
          GlobalSecondaryIndex globalSecondaryIndex1 = getGlobalSecondaryIndex1();
          GlobalSecondaryIndex globalSecondaryIndex2 = getGlobalSecondaryIndex2();
          CreateTableRequest request =
              getCreateTableRequest(globalSecondaryIndex1, globalSecondaryIndex2, tableName);
          dynamoDbClient.createTable(request);
            log.info("DynamoDB table '{}' created.", tableName);
        }
    }

  public static CreateTableRequest getCreateTableRequest(GlobalSecondaryIndex globalSecondaryIndex1,
                                                         GlobalSecondaryIndex globalSecondaryIndex2, String tableName1) {
    CreateTableRequest request = CreateTableRequest.builder()
              .tableName(tableName1)
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
              globalSecondaryIndex1,
              globalSecondaryIndex2
          )
              .build();
    return request;
  }

  public static GlobalSecondaryIndex getGlobalSecondaryIndex1() {
    GlobalSecondaryIndex globalSecondaryIndex = GlobalSecondaryIndex.builder()
        .indexName("gs-index-1")
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .keySchema(
            KeySchemaElement.builder().attributeName("gs1pk").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("gs1sk").keyType(KeyType.RANGE).build())
        .projection(projection -> projection.nonKeyAttributes("type", "createdAt", "s3location")
            .projectionType("INCLUDE"))
        .build();
    return globalSecondaryIndex;
  }

  public static GlobalSecondaryIndex getGlobalSecondaryIndex2() {
    GlobalSecondaryIndex globalSecondaryIndex = GlobalSecondaryIndex.builder()
        .indexName("gs-index-2")
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .keySchema(
            KeySchemaElement.builder().attributeName("gs2pk").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("gs2sk").keyType(KeyType.RANGE).build())
        .projection(projection -> projection.nonKeyAttributes("type", "createdAt", "s3location")
            .projectionType("INCLUDE"))
        .build();
    return globalSecondaryIndex;
  }
}
