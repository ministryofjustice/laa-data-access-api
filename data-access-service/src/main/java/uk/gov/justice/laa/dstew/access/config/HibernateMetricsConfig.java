package uk.gov.justice.laa.dstew.access.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.metrics.EntityOperationMetricsListener;

/**
 * Configuration for registering Hibernate event listeners for entity metrics.
 *
 * <p>Registers the EntityOperationMetricsListener with Hibernate's EventListenerRegistry
 * to capture entity lifecycle events for monitoring and observability.</p>
 *
 * <p>Uses ObjectProvider for safe resolution - gracefully skips if EntityManagerFactory
 * is not available (e.g., in tests without JPA).</p>
 */
@Configuration
@Slf4j
public class HibernateMetricsConfig implements ApplicationListener<ApplicationReadyEvent> {

  private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;
  private final EntityOperationMetricsListener entityOperationMetricsListener;

  public HibernateMetricsConfig(ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider,
      EntityOperationMetricsListener entityOperationMetricsListener) {
    this.entityManagerFactoryProvider = entityManagerFactoryProvider;
    this.entityOperationMetricsListener = entityOperationMetricsListener;
  }

  /**
   * Registers the EntityOperationMetricsListener with Hibernate's EventListenerRegistry.
   */
  public void registerListeners() {
    try {
      EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.getIfAvailable();

      if (entityManagerFactory == null) {
        log.debug("EntityManagerFactory not available, skipping listener registration");
        return;
      }

      SessionFactoryImpl sessionFactory = entityManagerFactory
          .unwrap(SessionFactoryImpl.class);

      if (sessionFactory == null) {
        log.debug("SessionFactory not available, skipping listener registration");
        return;
      }

      EventListenerRegistry listenerRegistry = sessionFactory.getEventListenerRegistry();

      listenerRegistry.appendListeners(EventType.POST_LOAD, entityOperationMetricsListener);
      listenerRegistry.appendListeners(EventType.PRE_INSERT, entityOperationMetricsListener);
      listenerRegistry.appendListeners(EventType.PRE_UPDATE, entityOperationMetricsListener);
      listenerRegistry.appendListeners(EventType.PRE_DELETE, entityOperationMetricsListener);

      log.info("Registered EntityOperationMetricsListener with Hibernate EventListenerRegistry");
    } catch (Exception e) {
      log.warn("Failed to register EntityOperationMetricsListener, metrics may be incomplete", e);
      // Don't throw exception - allow application to continue even if metrics listener registration fails
    }
  }

  /**
   * Triggers listener registration after the application is ready to ensure all beans are initialized.
   *
   * @param event application ready event.
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    registerListeners();
  }
}
