package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.commandhandling.AsynchronousCommandBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import uk.gov.justice.laa.dstew.access.config.interceptor.CreateApplicationSchemaValidationDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Runs each command in its own worker unit of work, including commands dispatched by sagas. */
@org.springframework.context.annotation.Configuration
public class AxonCommandBusConfig {

  /**
   * Replaces Axon's synchronous local command bus while preserving its standard instrumentation.
   */
  @Bean(destroyMethod = "shutdown")
  @Qualifier("localSegment")
  AsynchronousCommandBus commandBus(
      TransactionManager transactionManager,
      Configuration axonConfiguration,
      DuplicateCommandHandlerResolver duplicateCommandHandlerResolver,
      ServiceNameMetadataDispatchInterceptor serviceNameMetadataDispatchInterceptor,
      CreateApplicationSchemaValidationDispatchInterceptor
          createApplicationSchemaValidationDispatchInterceptor) {
    AsynchronousCommandBus commandBus =
        AsynchronousCommandBus.builder()
            .transactionManager(transactionManager)
            .duplicateCommandHandlerResolver(duplicateCommandHandlerResolver)
            .spanFactory(axonConfiguration.getComponent(CommandBusSpanFactory.class))
            .messageMonitor(axonConfiguration.messageMonitor(CommandBus.class, "commandBus"))
            .build();
    commandBus.registerHandlerInterceptor(
        new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));
    commandBus.registerDispatchInterceptor(serviceNameMetadataDispatchInterceptor);
    commandBus.registerDispatchInterceptor(createApplicationSchemaValidationDispatchInterceptor);
    return commandBus;
  }
}
