package uk.gov.justice.laa.dstew.access.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import uk.gov.justice.laa.dstew.access.entity.dynamo.DomainEventDynamoDb;

/**
 * Configuration class for AWS-related beans and settings.
 */
@Configuration
public class AwsConfig {

  // values are read from application properties or environment variables
  @Value("${aws.endpoint}")
  private String awsEndpoint;

  @Value("${aws.region:eu-west-2}")
  private String awsRegion;

  @Value("${aws.dynamodb.table-name:domain-events}")
  private String tableName;


  /**
   * Create and configure a DynamoDbClient bean for interacting with DynamoDB.
   *
   * @return a configured DynamoDbClient instance.
   */
  @Profile("!test") // In tests, we can use a different configuration or mock
  @Bean()
  public DynamoDbClient dynamoDbClient() {
    DynamoDbClientBuilder builder = DynamoDbClient.builder()
        .region(Region.of(awsRegion));

    // If an explicit endpoint is provided (localstack, etc.) configure it with static credentials
    if (awsEndpoint != null && !awsEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(awsEndpoint));
      // Use static credentials for LocalStack or custom endpoint
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
    } else {
      // In Kubernetes, use default provider chain which picks up IRSA credentials
      builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }

    return builder.build();
  }

  /**
   * Create a DynamoDbEnhancedClient bean that wraps the DynamoDbClient for higher-level operations.
   *
   * @return a configured DynamoDbEnhancedClient instance.
   */
  @Bean("!test") // In tests, we can use a different configuration or mock
  public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient())
        .build();
  }

  @Profile("!test")
  @Bean
  public DynamoDbTable<DomainEventDynamoDb> eventTable() {
    return dynamoDbEnhancedClient().table(tableName, TableSchema.fromBean(DomainEventDynamoDb.class));
  }

  /**
   * Create and configure an S3Client bean for interacting with S3.
   *
   * @return a configured S3Client instance.
   */
  @Profile("!test")
  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder = S3Client.builder()
        .forcePathStyle(true)
        .serviceConfiguration(S3Configuration.builder().build())
        .region(Region.of(awsRegion));

    // If an explicit endpoint is provided (localstack, etc.) configure it with static credentials
    if (awsEndpoint != null && !awsEndpoint.isBlank()) {
      builder.endpointOverride(URI.create(awsEndpoint));
      // Use static credentials for LocalStack or custom endpoint
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
    } else {
      // In Kubernetes, use default provider chain which picks up IRSA credentials
      builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }

    return builder.build();
  }

  /**
   * Executor for application domain event processing and other async tasks.
   * In production, this is a bounded thread pool. In tests, you can override this bean with SyncTaskExecutor.
   */
  @Bean(name = "applicationTaskExecutor")
  public ThreadPoolTaskExecutor applicationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("app-events-");
    executor.initialize();
    return executor;
  }

}
