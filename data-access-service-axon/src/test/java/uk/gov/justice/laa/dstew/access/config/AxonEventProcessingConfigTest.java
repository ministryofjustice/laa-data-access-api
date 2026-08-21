package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.config.EventProcessorSettings;
import org.junit.jupiter.api.Test;

class AxonEventProcessingConfigTest {

  private final AxonEventProcessingConfig config = new AxonEventProcessingConfig();

  @Test
  void priorAuthorityProjectionProcessorBeanUsesPooledStreamingWithCorrectNamespace() {
    EventProcessorDefinition definition = config.priorAuthorityProjectionProcessor();

    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("prior-authority-projection");
    assertThat(definition.mode()).isEqualTo(EventProcessorSettings.ProcessorMode.POOLED);
  }
}
