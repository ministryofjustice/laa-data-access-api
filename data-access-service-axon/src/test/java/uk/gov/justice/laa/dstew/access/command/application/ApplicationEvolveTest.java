package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
}
