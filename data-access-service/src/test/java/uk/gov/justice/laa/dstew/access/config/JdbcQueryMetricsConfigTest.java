package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcQueryMetricsConfigTest {

  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    JdbcQueryMetricsConfig config = new JdbcQueryMetricsConfig();
    meterRegistry.config().meterFilter(config.jdbcQueryHistogramFilter());
  }

  @Test
  void jdbcQueryTimer_publishesHistogramBuckets() {
    Timer timer = Timer.builder("jdbc.query").publishPercentileHistogram().register(meterRegistry);

    timer.record(java.time.Duration.ofMillis(50));

    assertThat(meterRegistry.find("jdbc.query").timer()).isNotNull();
    assertThat(meterRegistry.find("jdbc.query").meters()).isNotEmpty();
  }

  @Test
  void jdbcQueryActiveTimer_alsoMatchesFilter() {
    Timer timer = Timer.builder("jdbc.query.active").register(meterRegistry);

    timer.record(java.time.Duration.ofMillis(10));

    assertThat(meterRegistry.find("jdbc.query.active").timer()).isNotNull();
  }

  @Test
  void unrelatedMeter_notAffectedByFilter() {
    Timer timer = Timer.builder("http.server.requests").register(meterRegistry);

    timer.record(java.time.Duration.ofMillis(100));

    assertThat(meterRegistry.find("http.server.requests").timer()).isNotNull();
  }
}
