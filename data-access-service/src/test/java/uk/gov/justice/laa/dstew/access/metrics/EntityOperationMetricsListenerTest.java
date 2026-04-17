package uk.gov.justice.laa.dstew.access.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreUpdateEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityOperationMetricsListenerTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final EntityOperationMetricsListener listener =
      new EntityOperationMetricsListener(meterRegistry);

  @Mock private PostLoadEvent postLoadEvent;
  @Mock private PreInsertEvent preInsertEvent;
  @Mock private PreUpdateEvent preUpdateEvent;
  @Mock private PreDeleteEvent preDeleteEvent;

  @Test
  void onPostLoad_recordsReadOperation() {
    when(postLoadEvent.getEntity()).thenReturn(new DummyEntity());

    listener.onPostLoad(postLoadEvent);

    Counter counter =
        meterRegistry
            .find("jpa.entities")
            .tags("entity", "DummyEntity", "operation", "read")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void onPreInsert_recordsCreateOperation_returnsFalse() {
    when(preInsertEvent.getEntity()).thenReturn(new DummyEntity());

    boolean result = listener.onPreInsert(preInsertEvent);

    assertThat(result).isFalse();
    Counter counter =
        meterRegistry
            .find("jpa.entities")
            .tags("entity", "DummyEntity", "operation", "create")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void onPreUpdate_recordsUpdateOperation_returnsFalse() {
    when(preUpdateEvent.getEntity()).thenReturn(new DummyEntity());

    boolean result = listener.onPreUpdate(preUpdateEvent);

    assertThat(result).isFalse();
    Counter counter =
        meterRegistry
            .find("jpa.entities")
            .tags("entity", "DummyEntity", "operation", "update")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void onPreDelete_recordsDeleteOperation_returnsFalse() {
    when(preDeleteEvent.getEntity()).thenReturn(new DummyEntity());

    boolean result = listener.onPreDelete(preDeleteEvent);

    assertThat(result).isFalse();
    Counter counter =
        meterRegistry
            .find("jpa.entities")
            .tags("entity", "DummyEntity", "operation", "delete")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void nullEntity_doesNotRecordMetric() {
    when(postLoadEvent.getEntity()).thenReturn(null);

    listener.onPostLoad(postLoadEvent);

    Counter counter = meterRegistry.find("jpa.entities").counter();
    assertThat(counter).isNull();
  }

  @Test
  void multipleCallsSameOperation_incrementsCounter() {
    when(postLoadEvent.getEntity()).thenReturn(new DummyEntity());

    listener.onPostLoad(postLoadEvent);
    listener.onPostLoad(postLoadEvent);

    Counter counter =
        meterRegistry
            .find("jpa.entities")
            .tags("entity", "DummyEntity", "operation", "read")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(2.0);
  }

  private static class DummyEntity {}
}
