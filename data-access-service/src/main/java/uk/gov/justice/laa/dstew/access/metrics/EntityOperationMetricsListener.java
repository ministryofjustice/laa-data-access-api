package uk.gov.justice.laa.dstew.access.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to Hibernate entity lifecycle events and records metrics.
 *
 * <p>Tracks create, read, update, and delete operations on JPA entities
 * and exposes them as Micrometer counters for Prometheus monitoring.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EntityOperationMetricsListener implements
    PostLoadEventListener,
    PreInsertEventListener,
    PreUpdateEventListener,
    PreDeleteEventListener {

  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

  @Override
  public void onPostLoad(PostLoadEvent event) {
    recordEntityOperation(event.getEntity(), "read");
  }

  @Override
  public boolean onPreInsert(PreInsertEvent event) {
    recordEntityOperation(event.getEntity(), "create");
    return false;
  }

  @Override
  public boolean onPreUpdate(PreUpdateEvent event) {
    recordEntityOperation(event.getEntity(), "update");
    return false;
  }

  @Override
  public boolean onPreDelete(PreDeleteEvent event) {
    recordEntityOperation(event.getEntity(), "delete");
    return false;
  }

  /**
   * Records a single entity operation metric.
   *
   * @param entity    the entity being operated on
   * @param operation the operation type: "create", "read", "update", or "delete"
   */
  private void recordEntityOperation(Object entity, String operation) {
    if (entity == null) {
      return;
    }

    String entityName = entity.getClass().getSimpleName();
    String cacheKey = entityName + ":" + operation;

    counterCache.computeIfAbsent(cacheKey, key ->
        Counter.builder("jpa.entities")
            .description("JPA entity operations")
            .tag("entity", entityName)
            .tag("operation", operation)
            .register(meterRegistry)
    ).increment();

    if (log.isDebugEnabled()) {
      log.debug("Entity operation recorded: entity={}, operation={}", entityName, operation);
    }
  }
}
