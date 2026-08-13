package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.model.AutoGranted;

class AutoGrantedMapperTest {

  @ParameterizedTest
  @MethodSource("legacyStates")
  void mapsLegacyFlagToExplicitAssessmentState(Boolean legacyFlag, AutoGranted expected) {
    assertThat(AutoGrantedMapper.fromLegacyFlag(legacyFlag)).isEqualTo(expected);
  }

  private static java.util.stream.Stream<Arguments> legacyStates() {
    return java.util.stream.Stream.of(
        Arguments.of(null, AutoGranted.PENDING),
        Arguments.of(true, AutoGranted.AUTOGRANTED),
        Arguments.of(false, AutoGranted.MANUAL));
  }
}
