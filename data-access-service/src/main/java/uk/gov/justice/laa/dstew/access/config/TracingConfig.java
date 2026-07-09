package uk.gov.justice.laa.dstew.access.config;

import brave.sampler.Sampler;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Configuration for Micrometer Tracing with baggage-based correlation ID propagation. */
@Configuration
@Slf4j
@ExcludeFromGeneratedCodeCoverage
public class TracingConfig {

  /**
   * Configures tracing sampler to sample all traces. For production, consider probability-based
   * sampling to reduce overhead.
   *
   * @return sampler that samples all traces (adjust for production)
   */
  @Bean
  public Sampler defaultSampler() {
    return Sampler.ALWAYS_SAMPLE;
  }

  /**
   * Provides helper for accessing trace and span IDs from current context.
   *
   * @param tracer the Micrometer tracer
   * @return the tracing helper
   */
  @Bean
  public TracingHelper tracingHelper(Tracer tracer) {
    log.info("Configuring Micrometer Tracing with baggage-based correlation ID propagation");
    return new TracingHelper(tracer);
  }

  /** Helper class to access tracing information from the current context. */
  @ExcludeFromGeneratedCodeCoverage
  public static class TracingHelper {
    private final Tracer tracer;

    public TracingHelper(Tracer tracer) {
      this.tracer = tracer;
    }

    /**
     * Gets the current trace ID (hex format for distributed tracing).
     *
     * @return the trace ID or null if no trace is active
     */
    public String getTraceId() {
      if (tracer.currentSpan() != null) {
        return tracer.currentSpan().context().traceId();
      }
      return null;
    }

    /**
     * Gets the current span ID.
     *
     * @return the span ID or null if no span is active
     */
    public String getSpanId() {
      if (tracer.currentSpan() != null) {
        return tracer.currentSpan().context().spanId();
      }
      return null;
    }

    /**
     * Gets the correlation ID from baggage.
     *
     * @return the correlation ID (UUID7) or null if not set
     */
    public String getCorrelationId() {
      if (tracer.currentSpan() != null) {
        return tracer.getBaggage("correlationId").get();
      }
      return null;
    }
  }
}
