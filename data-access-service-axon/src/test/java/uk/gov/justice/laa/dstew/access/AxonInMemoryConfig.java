package uk.gov.justice.laa.dstew.access;

import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Provides infrastructure-free Axon storage for fast application tests. */
@TestConfiguration
public class AxonInMemoryConfig {

  @Bean
  @Primary
  EventStore eventStore() {
    return EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
  }

  @Bean
  @Primary
  TokenStore tokenStore() {
    return new InMemoryTokenStore();
  }
}
