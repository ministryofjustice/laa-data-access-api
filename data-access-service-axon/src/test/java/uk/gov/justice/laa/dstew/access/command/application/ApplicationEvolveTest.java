package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;

/** Unit tests for {@link ApplicationEvolve} decision-fold behaviour. */
class ApplicationEvolveTest {

  @Test
  void givenDecisionMadeEventWithGranted_whenApply_thenSetsOverallDecision() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    state.applicationId = applicationId;
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 1L, "GRANTED", AutoGrantedState.AUTOGRANTED, Instant.now());

    ApplicationEvolve.apply(state, event);

    assertThat(state.overallDecision).isEqualTo("GRANTED");
  }

  @Test
  void givenDecisionMadeEventWithRefused_whenApply_thenSetsOverallDecision() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    state.applicationId = applicationId;
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 1L, "REFUSED", AutoGrantedState.MANUAL, Instant.now());

    ApplicationEvolve.apply(state, event);

    assertThat(state.overallDecision).isEqualTo("REFUSED");
  }

  @ParameterizedTest
  @CsvSource({"GRANTED,APPLICATION_GRANTED", "REFUSED,APPLICATION_REFUSED"})
  void givenDecisionEvent_whenApplied_thenUpdatesApplicationStatus(
      String overallDecision, String expectedStatus) {
    ApplicationState state = new ApplicationState();

    ApplicationEvolve.apply(
        state,
        new ApplicationDecisionMadeEvent(
            UUID.randomUUID(), 2L, 2L, overallDecision, AutoGrantedState.MANUAL, Instant.now()));

    assertThat(state.status).isEqualTo(expectedStatus);
  }

  @Test
  void givenDecisionEventWithoutOverallDecision_whenApplied_thenRetainsCurrentApplicationStatus() {
    ApplicationState state = new ApplicationState();
    state.status = ApplicationStatus.APPLICATION_SUBMITTED.getValue();

    ApplicationEvolve.apply(
        state,
        new ApplicationDecisionMadeEvent(
            UUID.randomUUID(), 2L, 2L, null, AutoGrantedState.MANUAL, Instant.now()));

    assertThat(state.status).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.getValue());
  }
}
