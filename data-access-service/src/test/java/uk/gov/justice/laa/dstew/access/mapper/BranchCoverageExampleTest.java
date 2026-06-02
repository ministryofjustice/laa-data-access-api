package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test covering only the null/blank branch of {@link BranchCoverageExample}. Combined with
 * {@code BranchCoverageExampleIT} this should yield 100% branch coverage in the aggregated JaCoCo
 * report.
 *
 * <p>Left in to keep proof that the aggregated report is working
 */
class BranchCoverageExampleTest {

  private final BranchCoverageExample example = new BranchCoverageExample();

  @Test
  void nullInputReturnsEmpty() {
    assertThat(example.format(null)).isEqualTo("empty");
  }

  @Test
  void blankInputReturnsEmpty() {
    assertThat(example.format("   ")).isEqualTo("empty");
  }
}
