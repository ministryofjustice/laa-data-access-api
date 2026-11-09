package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.RegisteredContentReaders;
import uk.gov.justice.laa.dstew.access.eventcontent.axon.ContentReadingEventSource;
import uk.gov.justice.laa.dstew.access.query.genesis.GenesisStreamProjection;

/** Defines isolated processors for projections and synchronous command-side route updates. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
public class AxonEventProcessingConfig {

  /** Builds the application summary from event content. */
  @Bean
  EventProcessorDefinition applicationSummaryProcessor(
      ObjectProvider<EventContentSeparator> separator,
      ObjectProvider<EventContentStore> store,
      ObjectProvider<EventConverter> converter) {
    return contentReadingProcessor(
        RegisteredContentReaders.APPLICATION_SUMMARY, separator, store, converter);
  }

  /** Writes genesis stream pointers; it needs no content, only IDs and timestamps. */
  @Bean
  EventProcessorDefinition genesisStreamProcessor() {
    return pooledStreamingProcessor(GenesisStreamProjection.PROCESSING_GROUP);
  }

  @Bean
  EventProcessorDefinition linkedApplicationGroupProjectionProcessor() {
    return pooledStreamingProcessor("linked-application-group-projection");
  }

  @Bean
  EventProcessorDefinition linkedGroupWorkItemLifecycleProcessor() {
    return pooledStreamingProcessor("linked-group-work-item-lifecycle");
  }

  @Bean
  EventProcessorDefinition priorAuthorityProjectionProcessor(
      ObjectProvider<EventContentSeparator> separator,
      ObjectProvider<EventContentStore> store,
      ObjectProvider<EventConverter> converter) {
    return contentReadingProcessor(
        RegisteredContentReaders.PRIOR_AUTHORITY_PROJECTION, separator, store, converter);
  }

  /** Keeps the disposable work-list projection replayable and isolated from command routing. */
  @Bean
  EventProcessorDefinition workListProjectionProcessor() {
    return pooledStreamingProcessor("work-list-projection");
  }

  /** Keeps write-side application-group routes synchronous with group membership events. */
  @Bean
  EventProcessorDefinition applicationGroupRouteProcessor() {
    return EventProcessorDefinition.subscribingMatching("application-group-route")
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

  /** A streaming processor that receives event content; it must be a registered content reader. */
  private EventProcessorDefinition contentReadingProcessor(
      String processingGroup,
      ObjectProvider<EventContentSeparator> separator,
      ObjectProvider<EventContentStore> store,
      ObjectProvider<EventConverter> converter) {
    return EventProcessorDefinition.pooledStreamingMatching(processingGroup)
        .customized(
            configuration ->
                configuration
                    .eventSource(
                        ContentReadingEventSource.forProcessor(
                            processingGroup,
                            configuration.eventSource(),
                            separator::getObject,
                            store::getObject,
                            converter::getObject))
                    .errorHandler(PropagatingErrorHandler.instance()));
  }

  private EventProcessorDefinition pooledStreamingProcessor(String processingGroup) {
    return EventProcessorDefinition.pooledStreamingMatching(processingGroup)
        .customized(
            configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
  }
}
