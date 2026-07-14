package uk.gov.justice.laa.dstew.access.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Configures Axon's event and token stores to use the application JPA transaction boundary. */
@Configuration
@EntityScan(
    basePackageClasses = {
      DomainEventEntry.class,
      org.axonframework.eventhandling.tokenstore.jpa.TokenEntry.class
    })
@ConditionalOnProperty(
    name = "axon.eventstore.jpa.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AxonJpaConfig {

  @Bean
  @ConditionalOnMissingBean
  EntityManagerProvider entityManagerProvider() {
    return new ContainerManagedEntityManagerProvider();
  }

  @Bean
  @ConditionalOnMissingBean(TransactionManager.class)
  TransactionManager axonTransactionManager(PlatformTransactionManager transactionManager) {
    return new SpringTransactionManager(transactionManager);
  }

  @Bean
  EmbeddedEventStore eventStore(EventStorageEngine storageEngine) {
    return EmbeddedEventStore.builder().storageEngine(storageEngine).build();
  }

  @Bean
  EventStorageEngine storageEngine(
      EntityManagerProvider entityManagerProvider,
      TransactionManager transactionManager,
      Serializer serializer) {
    return JpaEventStorageEngine.builder()
        .entityManagerProvider(entityManagerProvider)
        .transactionManager(transactionManager)
        .eventSerializer(serializer)
        .snapshotSerializer(serializer)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(TokenStore.class)
  TokenStore tokenStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
    return JpaTokenStore.builder()
        .entityManagerProvider(entityManagerProvider)
        .serializer(serializer)
        .build();
  }
}
