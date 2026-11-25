package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.config.EventProcessorSettings;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.axon.ContentReadingEventSource;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.observability.ReportingErrorHandler;

@ExtendWith(MockitoExtension.class)
class AxonEventProcessingConfigTest {

  @Mock private PooledStreamingEventProcessorConfiguration pooledConfiguration;
  @Mock private ObjectProvider<EventContentSeparator> separator;
  @Mock private ObjectProvider<EventContentStore> store;
  @Mock private ObjectProvider<EventConverter> converter;
  @Mock private StreamableEventSource eventSource;

  private final ReportingErrorHandler reportingErrorHandler =
      new ReportingErrorHandler(new ErrorReporter(), Duration.ofMinutes(5));

  private final AxonEventProcessingConfig config =
      new AxonEventProcessingConfig(reportingErrorHandler);

  @Test
  void priorAuthorityProjectionProcessorBeanUsesPooledStreamingWithCorrectNamespace() {
    EventProcessorDefinition definition =
        config.priorAuthorityProjectionProcessor(null, null, null);

    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("prior-authority-projection");
    assertThat(definition.mode()).isEqualTo(EventProcessorSettings.ProcessorMode.POOLED);
  }

  @Test
  void applicationGroupRouteProcessorBeanUsesSubscribingWithCorrectNamespace() {
    EventProcessorDefinition definition = config.applicationGroupRouteProcessor();

    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("application-group-route");
    assertThat(definition.mode()).isEqualTo(EventProcessorSettings.ProcessorMode.SUBSCRIBING);
  }

  @Test
  void pooledStreamingProcessorBeanUsesReportingErrorHandler() {
    EventProcessorDefinition definition = config.workListProjectionProcessor();
    PooledStreamingEventProcessorConfiguration configuration =
        mock(PooledStreamingEventProcessorConfiguration.class);
    when(configuration.errorHandler(any())).thenReturn(configuration);

    definition.applySettings(configuration);

    verify(configuration).errorHandler(reportingErrorHandler);
  }

  @Test
  void subscribingProcessorBeanUsesPropagatingErrorHandler() {
    EventProcessorDefinition definition = config.applicationGroupRouteProcessor();
    SubscribingEventProcessorConfiguration configuration =
        mock(SubscribingEventProcessorConfiguration.class);
    when(configuration.errorHandler(any())).thenReturn(configuration);

    definition.applySettings(configuration);

    verify(configuration).errorHandler(PropagatingErrorHandler.instance());
  }

  @Test
  void givenProjectionReadingContent_whenDefined_thenItsSourceReadsContent() {
    when(pooledConfiguration.eventSource()).thenReturn(eventSource);
    when(pooledConfiguration.eventSource(any())).thenReturn(pooledConfiguration);
    when(pooledConfiguration.errorHandler(any())).thenReturn(pooledConfiguration);
    EventProcessorDefinition definition =
        config.applicationProjectionProcessor(separator, store, converter);

    definition.applySettings(pooledConfiguration);

    assertThat(definition.name()).isEqualTo("application-projection");
    verify(pooledConfiguration).eventSource(any(ContentReadingEventSource.class));
    verify(pooledConfiguration).errorHandler(reportingErrorHandler);
  }

  @Test
  void givenProjectionNotReadingContent_whenDefined_thenItsSourceIsUnchanged() {
    when(pooledConfiguration.errorHandler(any())).thenReturn(pooledConfiguration);
    EventProcessorDefinition definition =
        config.projectionProcessor(PlainProjection.class, separator, store, converter);

    definition.applySettings(pooledConfiguration);

    assertThat(definition.name()).isEqualTo("plain-projection");
    verify(pooledConfiguration, never()).eventSource(any());
    verify(pooledConfiguration).errorHandler(reportingErrorHandler);
  }

  @Test
  void givenProjectionWithoutNamespace_whenDefined_thenRejected() {
    assertThatThrownBy(
            () -> config.projectionProcessor(UnnamedProjection.class, separator, store, converter))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(UnnamedProjection.class.getName() + " has no @Namespace naming its processor");
  }

  @Namespace("plain-projection")
  private static final class PlainProjection {}

  private static final class UnnamedProjection {}
}
