package uk.gov.justice.laa.dstew.access.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Configures histogram bucket publication for JDBC query metrics.
 *
 * <p>The YAML config {@code management.metrics.distribution.percentile-histogram.jdbc.query: true}
 * does not take effect for observation-created timers. This MeterFilter applies the histogram
 * configuration programmatically, ensuring {@code jdbc_query_seconds_bucket} metrics are published
 * for use by Grafana's {@code histogram_quantile()} queries.</p>
 */
@ExcludeFromGeneratedCodeCoverage
@Configuration
public class JdbcQueryMetricsConfig {

  @Bean
  MeterFilter jdbcQueryHistogramFilter() {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(Meter.@NonNull Id id,
                                                   @NonNull DistributionStatisticConfig config) {
        if (id.getName().startsWith("jdbc.query")) {
          return DistributionStatisticConfig.builder()
              .percentilesHistogram(true)
              .build()
              .merge(config);
        }
        return config;
      }
    };
  }
}
