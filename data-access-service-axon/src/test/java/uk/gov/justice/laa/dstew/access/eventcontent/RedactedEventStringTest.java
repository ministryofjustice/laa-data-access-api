package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RedactedEventStringTest {

  record Sample(@RetainedField String id, @Nullable String note) {}

  @Test
  void givenContentPresent_whenRendered_thenRetainedShownAndContentHidden() {
    assertThat(RedactedEventString.of(new Sample("id-1", "secret")))
        .isEqualTo("Sample[id=id-1, note=<content>]");
  }

  @Test
  void givenContentAbsent_whenRendered_thenContentShownAsNull() {
    assertThat(RedactedEventString.of(new Sample("id-1", null)))
        .isEqualTo("Sample[id=id-1, note=null]");
  }
}
