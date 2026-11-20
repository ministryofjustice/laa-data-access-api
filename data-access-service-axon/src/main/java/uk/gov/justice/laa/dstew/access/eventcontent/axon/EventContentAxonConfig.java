package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;
import uk.gov.justice.laa.dstew.access.eventcontent.jdbc.JdbcEventContentStore;

/** Adapts the framework-neutral event content model to Axon. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
public class EventContentAxonConfig {

  @Bean
  EventJson eventJson() {
    return new EventJson();
  }

  /** Axon serialises events with the mapper we own, so splitting and Axon agree exactly. */
  @Bean
  EventConverter eventConverter(EventJson json) {
    return new DelegatingEventConverter(new JacksonConverter(json.mapper()));
  }

  @Bean
  EventContentSeparator eventContentSeparator(EventJson json) {
    return new EventContentSeparator(json);
  }

  @Bean
  EventContentStore eventContentStore(
      JdbcTemplate jdbc, EventJson json, @Value("${axon.db.schema:axon}") String schema) {
    return new JdbcEventContentStore(jdbc, json, schema);
  }

  @Bean
  ConfigurationEnhancer contentSeparatingStorageEngine(
      ObjectProvider<EventContentSeparator> separator, ObjectProvider<EventContentStore> store) {
    return registry ->
        registry.registerDecorator(
            EventStorageEngine.class,
            0,
            (config, name, delegate) ->
                new ContentSeparatingStorageEngine(
                    delegate,
                    separator::getObject,
                    store::getObject,
                    () -> config.getComponent(EventConverter.class)));
  }
}
