package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for RestClient bean.
 */
@Configuration
public class RestClientConfig {

  @Bean
  RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }
}
