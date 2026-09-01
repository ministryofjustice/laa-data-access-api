package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Defines isolated processors for projections and the synchronous linked-application router. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
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
  EventProcessorDefinition linkedGroupWorkItemLifecycleProcessor() {
    return pooledStreamingProcessor("linked-group-work-item-lifecycle");
  }

  @Bean
  EventProcessorDefinition priorAuthorityProjectionProcessor() {
    return pooledStreamingProcessor("prior-authority-projection");
  }

  /** Keeps the disposable work-list projection replayable and isolated from command routing. */
  @Bean
  EventProcessorDefinition workListProjectionProcessor() {
    return pooledStreamingProcessor("work-list-projection");
  }

  @Bean
  EventProcessorDefinition linkedApplicationGroupRouterProcessor() {
    return EventProcessorDefinition.subscribingMatching("linked-application-group-router")
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }

  /** Keeps write-side work-item routes transactionally aligned with route-changing events. */
  @Bean
  EventProcessorDefinition workItemRouteProcessor() {
    return EventProcessorDefinition.subscribingMatching("work-item-route")
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }

  /**
   * Handles live submission events synchronously so publication can attach to their unit of work.
   */
  @Bean
  EventProcessorDefinition applicationSubmittedPublisherProcessor() {
    return EventProcessorDefinition.subscribingMatching("application-submitted-publisher")
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }

  private EventProcessorDefinition pooledStreamingProcessor(String processingGroup) {
    return EventProcessorDefinition.pooledStreamingMatching(processingGroup)
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }
}
