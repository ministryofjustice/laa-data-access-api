package uk.gov.justice.laa.dstew.access.mapper;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Demo class used to verify that the combined JaCoCo report merges unit and integration coverage.
 *
 * <p>Left in to keep proof that the aggregated report and verification is working
 */
public class BranchCoverageExample {

  /**
   * Three branches for testing. Null check is covered by unit tests and string check covered by
   * integration tests.
   *
   * <p>Unit test - 75% Integration test - 25%
   */
  public String format(String input) {
    if (input == null || input.isBlank()) {
      return "empty";
    }
    return input.trim();
  }

  /** ensures that the exclude code coverage annotation works for functions. */
  @ExcludeFromGeneratedCodeCoverage
  public String excludedFunction(String input) {
    if (input == null || input.isBlank()) {
      return "empty";
    }
    return "string";
  }

  /** ensures that the exclude code coverage annotation works for classes. */
  @ExcludeFromGeneratedCodeCoverage
  private static class ExcludedClass {
    public String untestedFunction(String input) {
      if (input == null || input.isBlank()) {
        return "empty";
      }
      return "string";
    }
  }
}
