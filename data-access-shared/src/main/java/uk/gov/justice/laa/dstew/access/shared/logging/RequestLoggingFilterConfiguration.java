package uk.gov.justice.laa.dstew.access.shared.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/** Spring config class to provide an injectable request log filter. */
@Configuration
public class RequestLoggingFilterConfiguration {

  /**
   * Creates and configures the request log filter. Excludes actuator endpoints.
   *
   * @return the configured request log filter.
   */
  @Bean
  public CommonsRequestLoggingFilter logFilter() {
    CommonsRequestLoggingFilter filter =
        new CommonsRequestLoggingFilter() {
          @Override
          protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri != null && uri.startsWith("/actuator");
          }
        };

    filter.setIncludeQueryString(true);
    filter.setIncludePayload(true);
    filter.setIncludeHeaders(true);
    filter.setIncludeClientInfo(true);
    filter.setMaxPayloadLength(50_000);

    return filter;
  }
}
