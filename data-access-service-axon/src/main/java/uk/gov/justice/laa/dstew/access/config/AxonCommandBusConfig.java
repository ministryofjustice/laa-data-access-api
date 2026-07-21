package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.justice.laa.dstew.access.config.interceptor.CreateApplicationSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Configures the default Axon command bus with dispatch interceptors and metadata correlation. */
@Configuration
public class AxonCommandBusConfig {

  /** Bridges Axon's unit of work to the Spring transaction used by JPA repositories. */
  @Bean
  @ConditionalOnMissingBean(TransactionManager.class)
  TransactionManager axonTransactionManager(PlatformTransactionManager transactionManager) {
    return new SpringTransactionManager(transactionManager);
  }

  /** Starts the Spring transaction before command handling and subscribing event processing. */
  @Bean
  ConfigurationEnhancer commandTransactionManager(TransactionManager transactionManager) {
    return configurer ->
        configurer.configureTransactionManager(configuration -> transactionManager);
  }

  /** Copies the request service name from commands onto the events they cause. */
  @Bean
  CorrelationDataProvider serviceNameCorrelationDataProvider() {
    return new SimpleCorrelationDataProvider(
        ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
  }

  /**
   * Registers dispatch interceptors on the Axon command bus during startup.
   *
   * <p>Interceptor registration is deferred to {@code config.onStart()} rather than {@code
   * configurer.onInitialize()} to avoid a circular Spring bean dependency. {@code onInitialize}
   * runs during {@code SpringAxonConfiguration.getObject()}, which is itself triggered by the
   * Spring {@code commandBus} bean creation. Calling {@code config.commandBus()} at that point
   * resolves to {@code SpringConfigurer.findBean(CommandBus.class)}, which asks Spring for the
   * {@code commandBus} bean that is still mid-creation — producing {@code
   * BeanCurrentlyInCreationException}. Deferring to {@code onStart} ensures all Spring singletons
   * are fully initialised before the interceptors are attached.
   *
   * <p>Axon's Spring Boot starter does not auto-register {@code MessageDispatchInterceptor} beans,
   * so explicit registration via {@code ConfigurerModule} is required (guardrail G1).
   */
  @Bean
  ConfigurationEnhancer commandDispatchInterceptors(
      ServiceNameMetadataDispatchInterceptor serviceNameInterceptor,
      CreateApplicationSchemaValidationDispatchInterceptor schemaInterceptor) {
    return configurer ->
        configurer.onInitialize(
            config ->
                config.onStart(
                    Integer.MIN_VALUE,
                    () -> {
                      config.commandBus().registerDispatchInterceptor(serviceNameInterceptor);
                      config.commandBus().registerDispatchInterceptor(schemaInterceptor);
                    }));
  }

  /**
   * Registers the linked-application-group router as a subscribing event processor with propagating
   * error handlers at both the listener and processor levels. Subscribing mode ensures the event
   * handler runs synchronously in the same Axon unit of work as the originating command, so a
   * {@link uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException} thrown when the
   * lead application does not exist propagates back to the HTTP caller as a 404.
   *
   * <p>{@link PropagatingErrorHandler} is used for both {@code
   * registerListenerInvocationErrorHandler} and {@code registerErrorHandler}: the default {@code
   * LoggingErrorHandler} would swallow exceptions from event handlers, breaking the synchronous
   * validation contract.
   *
   * <p>Registration is via {@code ConfigurerModule} (guardrail G1).
   */
  @Bean
  ConfigurationEnhancer linkedApplicationGroupRouterProcessingMode() {
    return configurer -> {
      var ep = configurer.eventProcessing();
      ep.registerSubscribingEventProcessor("linked-application-group-router");
      ep.registerListenerInvocationErrorHandler(
          "linked-application-group-router", config -> PropagatingErrorHandler.instance());
      ep.registerErrorHandler(
          "linked-application-group-router", config -> PropagatingErrorHandler.instance());
    };
  }
}
