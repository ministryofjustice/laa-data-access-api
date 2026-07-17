package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.config.ConfigurerModule;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.config.interceptor.CreateApplicationSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Configures the default Axon command bus with dispatch interceptors and metadata correlation. */
@Configuration
public class AxonCommandBusConfig {

  /** Copies the request service name from commands onto the events they cause. */
  @Bean
  CorrelationDataProvider serviceNameCorrelationDataProvider() {
    return new SimpleCorrelationDataProvider(
        ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
  }

  /**
   * Registers dispatch interceptors on the Axon command bus during initialisation. Axon's Spring
   * Boot starter does not auto-register MessageDispatchInterceptor beans, so explicit registration
   * via ConfigurerModule is required.
   */
  @Bean
  ConfigurerModule commandDispatchInterceptors(
      ServiceNameMetadataDispatchInterceptor serviceNameInterceptor,
      CreateApplicationSchemaValidationDispatchInterceptor schemaInterceptor) {
    return configurer ->
        configurer.onInitialize(
            config -> {
              config.commandBus().registerDispatchInterceptor(serviceNameInterceptor);
              config.commandBus().registerDispatchInterceptor(schemaInterceptor);
            });
  }
}
