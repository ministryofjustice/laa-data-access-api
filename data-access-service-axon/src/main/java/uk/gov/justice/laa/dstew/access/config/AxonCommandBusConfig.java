package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.interceptor.ContentSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;

/** Configures the default Axon command bus with dispatch interceptors and metadata correlation. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
public class AxonCommandBusConfig {

  /** Copies the request service name from commands onto the events they cause. */
  @Bean
  CorrelationDataProvider serviceNameCorrelationDataProvider() {
    return new SimpleCorrelationDataProvider(
        RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
        RequestMetadataDispatchInterceptor.CORRELATION_ID_METADATA_KEY,
        RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY);
  }

  @Bean
  ConfigurationEnhancer commandDispatchInterceptors(
      RequestMetadataDispatchInterceptor serviceNameInterceptor,
      ContentSchemaValidationDispatchInterceptor schemaInterceptor) {
    return registry ->
        registry
            .registerComponent(
                RequestMetadataDispatchInterceptor.class, config -> serviceNameInterceptor)
            .registerComponent(
                ContentSchemaValidationDispatchInterceptor.class, config -> schemaInterceptor);
  }
}
