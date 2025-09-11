package uk.gov.justice.laa.dstew.access.shared.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Spring config class to provide an injectable request log filter.
 */
@Configuration
public class RequestLoggingFilterConfiguration {

  /**
   * Creates and configures the request log filter.
   *
   * @return the configured request log filter.
   */
  @Bean
  public CommonsRequestLoggingFilter logFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
    filter.setIncludeQueryString(true);
    filter.setIncludePayload(true);
    filter.setIncludeHeaders(true);
    filter.setIncludeClientInfo(true);
    filter.setMaxPayloadLength(50_000);

    return filter;
  }
}