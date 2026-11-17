package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  @Test
  void givenClockConfig_whenClockRequested_thenUsesUtc() {
    var clock = new ClockConfig().clock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
