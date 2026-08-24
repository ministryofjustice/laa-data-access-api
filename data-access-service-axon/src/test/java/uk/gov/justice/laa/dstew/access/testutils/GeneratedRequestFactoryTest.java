package uk.gov.justice.laa.dstew.access.testutils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

class GeneratedRequestFactoryTest {
  private final GeneratedRequestFactory requests = new GeneratedRequestFactory("test");

  @Test
  void createsConsistentGrantedAndRefusedManualDecisions() {
    UUID proceedingId = UUID.randomUUID();

    assertThat(requests.decision(proceedingId, DecisionStatus.GRANTED).getOverallDecision())
        .isEqualTo(DecisionStatus.GRANTED);
    assertThat(
            requests
                .decision(proceedingId, DecisionStatus.GRANTED)
                .getProceedings()
                .getFirst()
                .getMeritsDecision()
                .getDecision())
        .isEqualTo(MeritsDecisionStatus.GRANTED);
    assertThat(requests.decision(proceedingId, DecisionStatus.REFUSED).getOverallDecision())
        .isEqualTo(DecisionStatus.REFUSED);
    assertThat(
            requests
                .decision(proceedingId, DecisionStatus.REFUSED)
                .getProceedings()
                .getFirst()
                .getMeritsDecision()
                .getDecision())
        .isEqualTo(MeritsDecisionStatus.REFUSED);
  }
}
