package uk.gov.justice.laa.dstew.access.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Exposes Axon projection health independently from unassessed-Application age. */
@Component
public class AxonProcessorMetrics implements ApplicationRunner {

  private final AxonConfiguration configuration;
  private final MeterRegistry meterRegistry;

  public AxonProcessorMetrics(AxonConfiguration configuration, MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    configuration.getComponents(StreamingEventProcessor.class).forEach(this::registerProcessor);
  }

  private void registerProcessor(String name, StreamingEventProcessor processor) {
    Gauge.builder("axon.event.processor.running", processor, value -> value.isRunning() ? 1 : 0)
        .tag("processor", name)
        .register(meterRegistry);
    Gauge.builder("axon.event.processor.error", processor, value -> value.isError() ? 1 : 0)
        .tag("processor", name)
        .register(meterRegistry);
    Gauge.builder(
            "axon.event.processor.caught.up",
            processor,
            value ->
                !value.processingStatus().isEmpty()
                        && value.processingStatus().values().stream()
                            .allMatch(status -> status.isCaughtUp() && !status.isErrorState())
                    ? 1
                    : 0)
        .tag("processor", name)
        .register(meterRegistry);
  }
}
