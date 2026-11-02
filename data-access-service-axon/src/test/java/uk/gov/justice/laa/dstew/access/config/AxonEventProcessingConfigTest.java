package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.config.EventProcessorSettings;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.messaging.eventhandling.processing.subscribing.SubscribingEventProcessorConfiguration;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.observability.ReportingErrorHandler;

class AxonEventProcessingConfigTest {

  private final ReportingErrorHandler reportingErrorHandler =
      new ReportingErrorHandler(new ErrorReporter(), Duration.ofMinutes(5));

  private final AxonEventProcessingConfig config =
      new AxonEventProcessingConfig(reportingErrorHandler);

  @Test
  void priorAuthorityProjectionProcessorBeanUsesPooledStreamingWithCorrectNamespace() {
    EventProcessorDefinition definition = config.priorAuthorityProjectionProcessor();

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
  void linkedGroupAssignmentProcessorBeanUsesSubscribingWithCorrectNamespace() {
    EventProcessorDefinition definition = config.linkedGroupAssignmentProcessor();

    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("linkedGroupAssignment");
    assertThat(definition.mode()).isEqualTo(EventProcessorSettings.ProcessorMode.SUBSCRIBING);
  }

  @Test
  void pooledStreamingProcessorBeanUsesReportingErrorHandler() {
    EventProcessorDefinition definition = config.priorAuthorityProjectionProcessor();
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
}
