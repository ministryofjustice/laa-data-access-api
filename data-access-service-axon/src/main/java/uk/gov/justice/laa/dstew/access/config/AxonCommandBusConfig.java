package uk.gov.justice.laa.dstew.access.config;


import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.correlation.SimpleCorrelationDataProvider;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.justice.laa.dstew.access.config.interceptor.CreateApplicationSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Configures the default Axon command bus with dispatch interceptors and metadata correlation. */
@Configuration
public class AxonCommandBusConfig {

//  /** Bridges Axon's unit of work to the Spring transaction used by JPA repositories. */
//  @Bean
//  @ConditionalOnMissingBean(TransactionManager.class)
//  TransactionManager axonTransactionManager(PlatformTransactionManager transactionManager) {
//    return ;
//  }

//  /** Starts the Spring transaction before command handling and subscribing event processing. */
//  @Bean
//  ConfigurationEnhancer commandTransactionManager(TransactionManager transactionManager) {
//    return registry -> registry.registerComponent(
//        TransactionManager.class, config ->  transactionManager
//    );
//  }

  /** Copies the request service name from commands onto the events they cause. */
  @Bean
  CorrelationDataProvider serviceNameCorrelationDataProvider() {
    return new SimpleCorrelationDataProvider(
        ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
  }


  @Bean
  ConfigurationEnhancer commandDispatchInterceptors(
      ServiceNameMetadataDispatchInterceptor serviceNameInterceptor,
      CreateApplicationSchemaValidationDispatchInterceptor schemaInterceptor, ServiceNameContext serviceNameContext) {
    return registry ->
        registry.
            registerComponent(ServiceNameMetadataDispatchInterceptor.class,
                config -> serviceNameInterceptor)
            .registerComponent(CreateApplicationSchemaValidationDispatchInterceptor.class,
                config -> schemaInterceptor);

  }


//  @Bean
//  ConfigurationEnhancer linkedApplicationGroupRouterProcessingMode() {
//    return registry ->
//        registry.r
//      ep.registerSubscribingEventProcessor("linked-application-group-router");
//      ep.registerListenerInvocationErrorHandler(
//          "linked-application-group-router", config -> PropagatingErrorHandler.instance());
//      ep.registerErrorHandler(
//          "linked-application-group-router", config -> PropagatingErrorHandler.instance());
//    };
//  }
}
