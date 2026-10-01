package uk.gov.justice.laa.dstew.access.testutils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

class GeneratedRequestFactoryTest {
  private final GeneratedRequestFactory requests = new GeneratedRequestFactory("test");

  @ParameterizedTest
  @ValueSource(strings = {"test", "integration-test", ""})
  void generatesValidUniqueReferencesAcrossWorkers(String runId) {
    var factory = new GeneratedRequestFactory(runId);
    List<String> references =
        IntStream.range(0, 1000)
            .parallel()
            .mapToObj(index -> factory.application(UUID.randomUUID(), index).getLaaReference())
            .toList();

    assertThat(references)
        .doesNotHaveDuplicates()
        .allSatisfy(
            reference ->
                assertThat(reference)
                    .matches("L-[0-9ABCDEFHJKLMNPRTUVWXY]{3}-[0-9ABCDEFHJKLMNPRTUVWXY]{3}"));
    assertThat(String.join("", references)).contains("U", "0");
  }

  @Test
  void retriesReferencesAlreadyGeneratedByThisFactory() {
    var first = requests.application(UUID.randomUUID(), 1);
    var second = requests.application(UUID.randomUUID(), 1);

    assertThat(second.getLaaReference())
        .matches("L-[0-9ABCDEFHJKLMNPRTUVWXY]{3}-[0-9ABCDEFHJKLMNPRTUVWXY]{3}")
        .isNotEqualTo(first.getLaaReference());
  }

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
