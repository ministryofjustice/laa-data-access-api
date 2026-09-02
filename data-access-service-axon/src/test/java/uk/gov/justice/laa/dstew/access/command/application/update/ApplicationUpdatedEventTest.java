package uk.gov.justice.laa.dstew.access.command.application.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationUpdatedEventTest {

  private ApplicationUpdatedEvent event(String previousStatus, String status) {
    return new ApplicationUpdatedEvent(
        UUID.randomUUID(), 1L, 1L, previousStatus, status, Instant.now());
  }

  @Test
  void givenPreviousStatusIsAlreadySubmitted_whenEnteredSubmitted_thenReturnsFalse() {
    assertThat(event("APPLICATION_SUBMITTED", "APPLICATION_SUBMITTED").enteredSubmitted())
        .isFalse();
  }

  @Test
  void givenTransitionToSubmitted_whenEnteredSubmitted_thenReturnsTrue() {
    assertThat(event("DRAFT", "APPLICATION_SUBMITTED").enteredSubmitted()).isTrue();
  }

  @Test
  void givenNeitherStatusIsSubmitted_whenEnteredSubmitted_thenReturnsFalse() {
    assertThat(event("DRAFT", "DRAFT").enteredSubmitted()).isFalse();
  }
}
