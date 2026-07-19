package uk.gov.justice.laa.dstew.access.config;

import javax.sql.DataSource;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationData;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;

/** Configures Axon's event and token stores to use the application JPA transaction boundary. */
@Configuration
@EntityScan(
    basePackageClasses = {
      DomainEventEntry.class,
      org.axonframework.eventhandling.tokenstore.jpa.TokenEntry.class,
      ApplicationReadModel.class,
      ApplicationData.class,
      Caseworker.class
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
  @ConditionalOnMissingBean(PersistenceExceptionResolver.class)
  PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource)
      throws Exception {
    return new SQLErrorCodesResolver(dataSource);
  }

  @Bean
  EmbeddedEventStore eventStore(EventStorageEngine storageEngine) {
    return EmbeddedEventStore.builder().storageEngine(storageEngine).build();
  }

  @Bean
  EventStorageEngine storageEngine(
      EntityManagerProvider entityManagerProvider,
      TransactionManager transactionManager,
      Serializer serializer,
      PersistenceExceptionResolver persistenceExceptionResolver) {
    return JpaEventStorageEngine.builder()
        .entityManagerProvider(entityManagerProvider)
        .transactionManager(transactionManager)
        .eventSerializer(serializer)
        .snapshotSerializer(serializer)
        .persistenceExceptionResolver(persistenceExceptionResolver)
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
