package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.config.interceptor.ContentSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Configures the default Axon command bus with dispatch interceptors and metadata correlation. */
@Configuration
public class AxonCommandBusConfig {

  /** Copies the request service name from commands onto the events they cause. */
  @Bean
  CorrelationDataProvider serviceNameCorrelationDataProvider() {
    return new SimpleCorrelationDataProvider(
        ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
        ServiceNameMetadataDispatchInterceptor.CORRELATION_ID_METADATA_KEY);
  }

  @Bean
  ConfigurationEnhancer commandDispatchInterceptors(
      ServiceNameMetadataDispatchInterceptor serviceNameInterceptor,
      ContentSchemaValidationDispatchInterceptor schemaInterceptor) {
    return registry ->
        registry
            .registerComponent(
                ServiceNameMetadataDispatchInterceptor.class, config -> serviceNameInterceptor)
            .registerComponent(
                ContentSchemaValidationDispatchInterceptor.class, config -> schemaInterceptor);
  }
}
