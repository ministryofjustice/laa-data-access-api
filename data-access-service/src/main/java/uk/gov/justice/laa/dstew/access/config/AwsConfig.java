package uk.gov.justice.laa.dstew.access.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
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

  @Value("${aws.access-key:}")
  private String awsAccessKey;

  @Value("${aws.secret-key:}")
  private String awsSecretKey;

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

    // If an explicit endpoint is provided (localstack, etc.) configure it
    if (awsEndpoint != null && !awsEndpoint.isBlank()) {
      builder = builder.endpointOverride(URI.create(awsEndpoint));
    }

    builder.credentialsProvider(getCredentialsProvider());

    return builder.build();
  }

  private AwsCredentialsProvider getCredentialsProvider() {
    // If explicit credentials are provided (local/dev), use them. Otherwise rely on the default provider
    // which in Kubernetes will pick up IRSA or other environment/metadata credentials.
    if (awsAccessKey != null && !awsAccessKey.isBlank() && awsSecretKey != null && !awsSecretKey.isBlank()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(awsAccessKey, awsSecretKey));
    }
    return DefaultCredentialsProvider.builder().build();
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
        .serviceConfiguration(S3Configuration.builder()
            .build())
        .region(Region.of(awsRegion));
    // If an explicit endpoint is provided (localstack, etc.) configure it
    if (awsEndpoint != null && !awsEndpoint.isBlank()) {
      builder = builder.endpointOverride(URI.create(awsEndpoint));
    }

    builder.credentialsProvider(getCredentialsProvider());
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
