package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Defines isolated processors for projections and the synchronous linked-application router. */
@Configuration
public class AxonEventProcessingConfig {

  @Bean
  EventProcessorDefinition applicationProjectionProcessor() {
    return pooledStreamingProcessor("application-projection");
  }

  @Bean
  EventProcessorDefinition applicationHistoryProjectionProcessor() {
    return pooledStreamingProcessor("application-history-projection");
  }

  @Bean
  EventProcessorDefinition linkedApplicationGroupProjectionProcessor() {
    return pooledStreamingProcessor("linked-application-group-projection");
  }

  @Bean
  EventProcessorDefinition linkedApplicationGroupInitializerProcessor() {
    return pooledStreamingProcessor("linked-application-group-initializer");
  }


  @Bean
  EventProcessorDefinition workItemGroupFanOutProcessor() {
    return pooledStreamingProcessor("work-item-group-fan-out");
  }

  @Bean
  EventProcessorDefinition linkedApplicationGroupRouterProcessor() {
    return EventProcessorDefinition.subscribingMatching("linked-application-group-router")
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }

  private EventProcessorDefinition pooledStreamingProcessor(String processingGroup) {
    return EventProcessorDefinition.pooledStreamingMatching(processingGroup)
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }
}
