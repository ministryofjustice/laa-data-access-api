package uk.gov.justice.laa.dstew.access.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.EventTrackerStatus;
import org.junit.jupiter.api.Test;

class AxonProcessorMetricsTest {

  @Test
  void givenLaggingFailedProcessor_whenMetricsAreRead_thenHealthIsSeparateFromReconciliation() {
    AxonConfiguration configuration = org.mockito.Mockito.mock(AxonConfiguration.class);
    StreamingEventProcessor processor = org.mockito.Mockito.mock(StreamingEventProcessor.class);
    EventTrackerStatus status = org.mockito.Mockito.mock(EventTrackerStatus.class);
    when(configuration.getComponents(StreamingEventProcessor.class))
        .thenReturn(Map.of("application-projection", processor));
    when(processor.isRunning()).thenReturn(true);
    when(processor.isError()).thenReturn(true);
    when(processor.processingStatus()).thenReturn(Map.of(0, status));
    when(status.isCaughtUp()).thenReturn(false);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    new AxonProcessorMetrics(configuration, meterRegistry).run(null);

    assertThat(
            meterRegistry
                .get("axon.event.processor.running")
                .tag("processor", "application-projection")
                .gauge()
                .value())
        .isEqualTo(1);
    assertThat(
            meterRegistry
                .get("axon.event.processor.error")
                .tag("processor", "application-projection")
                .gauge()
                .value())
        .isEqualTo(1);
    assertThat(
            meterRegistry
                .get("axon.event.processor.caught.up")
                .tag("processor", "application-projection")
                .gauge()
                .value())
        .isZero();
  }
}
