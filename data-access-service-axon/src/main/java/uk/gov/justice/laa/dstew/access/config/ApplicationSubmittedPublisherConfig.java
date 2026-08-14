package uk.gov.justice.laa.dstew.access.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.integrationevent.ApplicationSubmittedPublisher;
import uk.gov.justice.laa.dstew.access.integrationevent.SnsApplicationSubmittedPublisher;

/** Configures the SNS integration-event adapter when a topic ARN is supplied. */
@Configuration
@ConditionalOnExpression(
    "T(org.springframework.util.StringUtils).hasText('${application.integration-events.topic-arn:}')")
public class ApplicationSubmittedPublisherConfig {

  /** Builds the normal AWS SDK client, optionally overriding its endpoint for LocalStack. */
  @Bean
  SnsClient snsClient(
      @Value("${spring.cloud.aws.region.static}") String region,
      @Value("${spring.cloud.aws.endpoint:}") String endpointUrl) {
    var builder =
        SnsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build());
    if (!endpointUrl.isBlank()) {
      builder.endpointOverride(URI.create(endpointUrl));
    }
    return builder.build();
  }

  /** Creates the publisher for the configured shared Data Access topic. */
  @Bean
  ApplicationSubmittedPublisher applicationSubmittedPublisher(
      SnsClient snsClient,
      ObjectMapper objectMapper,
      @Value("${application.integration-events.topic-arn}") String topicArn) {
    return new SnsApplicationSubmittedPublisher(snsClient, objectMapper, topicArn);
  }
}
