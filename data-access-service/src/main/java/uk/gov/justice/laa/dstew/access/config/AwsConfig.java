package uk.gov.justice.laa.dstew.access.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Configuration class for AWS-related beans and settings.
 */
@Configuration
public class AwsConfig {

  // values are read from application properties or environment variables
  @Value("${aws.endpoint:}")
  private String awsEndpoint;

  @Value("${aws.region:eu-west-2}")
  private String awsRegion;

  @Value("${aws.access-key:}")
  private String awsAccessKey;

  @Value("${aws.secret-key:}")
  private String awsSecretKey;


  /**
   * Create and configure a DynamoDbClient bean for interacting with DynamoDB.
   *
   * @return a configured DynamoDbClient instance.
   */
  @Bean
  public DynamoDbClient dynamoDbClient() {
    DynamoDbClientBuilder builder = DynamoDbClient.builder()
        .region(Region.of(awsRegion));

    // If an explicit endpoint is provided (localstack, etc.) configure it
    if (awsEndpoint != null && !awsEndpoint.isBlank()) {
      builder = builder.endpointOverride(URI.create(awsEndpoint));
    }

    // If explicit credentials are provided (local/dev), use them. Otherwise rely on the default provider
    // which in Kubernetes will pick up IRSA or other environment/metadata credentials.
    if (awsAccessKey != null && !awsAccessKey.isBlank() && awsSecretKey != null && !awsSecretKey.isBlank()) {
      builder = builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(awsAccessKey, awsSecretKey)));
    } else {
      builder = builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }

    return builder.build();
  }

  /**
   * Create a DynamoDbEnhancedClient bean that wraps the DynamoDbClient for higher-level operations.
   *
   * @return a configured DynamoDbEnhancedClient instance.
   */
  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient())
        .build();
  }

  /**
   * Create and configure an S3Client bean for interacting with S3.
   *
   * @return a configured S3Client instance.
   */
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
    // If explicit credentials are provided (local/dev), use them. Otherwise rely on the default provider
    // which in Kubernetes will pick up IRSA or other environment/metadata credentials.
    if (awsAccessKey != null && !awsAccessKey.isBlank() && awsSecretKey != null && !awsSecretKey.isBlank()) {
      builder = builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(awsAccessKey, awsSecretKey)));
    } else {
      builder = builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    }
    return builder.build();
  }
}
