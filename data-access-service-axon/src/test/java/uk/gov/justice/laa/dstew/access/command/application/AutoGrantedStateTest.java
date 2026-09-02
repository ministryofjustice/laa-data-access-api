package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoGrantedStateTest {

  @Test
  void givenNullValue_whenFromDecisionFlag_thenReturnsPending() {
    assertThat(AutoGrantedState.fromDecisionFlag(null)).isEqualTo(AutoGrantedState.PENDING);
  }

  @Test
  void givenTrueValue_whenFromDecisionFlag_thenReturnsAutoGranted() {
    assertThat(AutoGrantedState.fromDecisionFlag(true)).isEqualTo(AutoGrantedState.AUTOGRANTED);
  }

  @Test
  void givenFalseValue_whenFromDecisionFlag_thenReturnsManual() {
    assertThat(AutoGrantedState.fromDecisionFlag(false)).isEqualTo(AutoGrantedState.MANUAL);
  }
}
