package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegisteredContentReadersTest {

  @Test
  void givenProcessor_whenRequired_thenOnlyRegisteredReadersPass() {
    assertThatCode(
            () -> RegisteredContentReaders.require(RegisteredContentReaders.APPLICATION_PROJECTION))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> RegisteredContentReaders.require("work-list-projection"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("work-list-projection is not a registered content reader");
  }
}
