package uk.gov.justice.laa.dstew.access.config;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import uk.gov.justice.laa.dstew.access.service.DynamoDbService;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.gov.justice.laa.dstew.access.utils.LocalstackContainerInitializer;

@Configuration
@Profile("test")
public class TestServiceConfiguration {




    @Bean
    public DynamoDbClient dynamoDbClient() {
        LocalStackContainer localstack = LocalstackContainerInitializer.getLocalstack();
        return DynamoDbClient.builder()
            .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    localstack.getAccessKey(),
                    localstack.getSecretKey()
                )
            )).build();
    }
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient())
            .build();
    }

  @Bean
  public DynamoDbService dynamoDbService(DynamoDbClient dynamoDbClient,
                                         DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                         @Value("${aws.dynamodb.table-name}") String tableName) {
    return new DynamoDbService(dynamoDbClient, dynamoDbEnhancedClient, tableName);
  }

  @Bean
  public S3Client s3Client() {
    LocalStackContainer localstack = LocalstackContainerInitializer.getLocalstack();
    return S3Client.builder()
        .endpointOverride(localstack.getEndpointOverride(S3))
        .region(Region.of(localstack.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                localstack.getAccessKey(),
                localstack.getSecretKey()
            )
        )).build();
  }

}
