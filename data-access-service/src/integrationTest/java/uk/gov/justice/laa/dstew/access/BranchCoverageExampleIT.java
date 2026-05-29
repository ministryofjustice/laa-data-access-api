package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.mapper.BranchCoverageExample;

/**
 * Integration test covering only the non-blank branch of {@link BranchCoverageExample}. No Spring
 * context or Docker dependency needed — the class under test is a plain POJO.
 *
 * <p>Combined with {@code BranchCoverageExampleTest} this should yield 100% branch coverage in the
 * aggregated JaCoCo report.
 *
 * <p>Left in to keep proof that the aggregated report is working
 */
class BranchCoverageExampleIT {

  private final BranchCoverageExample example = new BranchCoverageExample();

  @Test
  void nonBlankInputReturnsTrimmedValue() {
    assertThat(example.format("  hello  ")).isEqualTo("hello");
  }
}
