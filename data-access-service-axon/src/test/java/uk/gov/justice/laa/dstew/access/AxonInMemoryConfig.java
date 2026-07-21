package uk.gov.justice.laa.dstew.access;

import java.util.List;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Provides infrastructure-free Axon storage for fast application tests. */
@TestConfiguration
public class AxonInMemoryConfig {

  @Bean
  @Primary
  EventStore eventStore() {
    return EmbeddedEventStore.builder()
        .storageEngine(new UniquenessEnforcingInMemoryEventStorageEngine())
        .build();
  }

  @Bean
  @Primary
  TokenStore tokenStore() {
    return new InMemoryTokenStore();
  }

  private static final class UniquenessEnforcingInMemoryEventStorageEngine
      extends InMemoryEventStorageEngine {

    @Override
    public synchronized void appendEvents(List<? extends EventMessage<?>> events) {
      events.stream()
          .filter(DomainEventMessage.class::isInstance)
          .map(DomainEventMessage.class::cast)
          .filter(this::sequenceAlreadyStored)
          .findFirst()
          .ifPresent(
              event -> {
                throw new AggregateStreamCreationException(
                    "Event already stored for aggregate "
                        + event.getAggregateIdentifier()
                        + " at sequence "
                        + event.getSequenceNumber());
              });
      super.appendEvents(events);
    }

    private boolean sequenceAlreadyStored(DomainEventMessage<?> candidate) {
      return readEvents(candidate.getAggregateIdentifier(), candidate.getSequenceNumber())
          .asStream()
          .anyMatch(existing -> existing.getSequenceNumber() == candidate.getSequenceNumber());
    }
  }
}
