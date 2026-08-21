package uk.gov.justice.laa.dstew.access.testutils.massdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationLifecycle;

class ApplicationLifecycleTest {

  @Test
  void selectsTheSameLifecyclesForTheSameSeed() {
    var first = RandomGeneratorFactory.getDefault().create(42L);
    var second = RandomGeneratorFactory.getDefault().create(42L);

    for (int index = 0; index < 100; index++) {
      assertThat(ApplicationLifecycle.select(first)).isEqualTo(ApplicationLifecycle.select(second));
    }
  }

  @Test
  void selectsGrantedAndRefusedManualDecisions() {
    var random = RandomGeneratorFactory.getDefault().create(42L);
    Set<DecisionStatus> decisions = new HashSet<>();

    for (int index = 0; index < 1_000; index++) {
      ApplicationLifecycle lifecycle = ApplicationLifecycle.select(random);
      if (lifecycle.makeDecision()) {
        decisions.add(lifecycle.decisionStatus());
      }
    }

    assertThat(decisions).containsExactlyInAnyOrder(DecisionStatus.GRANTED, DecisionStatus.REFUSED);
  }

  @Test
  void enforcesLifecycleInvariants() {
    assertThatThrownBy(() -> new ApplicationLifecycle(true, true, false, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApplicationLifecycle(false, false, false, true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
