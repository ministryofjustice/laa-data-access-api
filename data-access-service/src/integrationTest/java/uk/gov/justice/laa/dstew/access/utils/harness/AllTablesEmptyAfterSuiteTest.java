package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * End-of-suite sentinel: verifies that every application-domain table is empty after the complete
 * integration-test suite has run.
 *
 * <p>This test is placed last in execution order (via {@link Order @Order(Integer.MAX_VALUE)}) so
 * it fires after all other integration tests and their teardowns have completed.
 *
 * <p>It is intentionally minimal — the heavy lifting is done by the {@link
 * DatabaseCleanlinessAssertion} that is already wired into every {@code @AfterEach} in {@link
 * BaseHarnessTest}. This class provides an explicit, named failure point in the test report if any
 * data leaks across the entire suite.
 */
@Order(Integer.MAX_VALUE)
public class AllTablesEmptyAfterSuiteTest extends BaseHarnessTest {

  @Order(2)
  @BeforeEach
  void removeCaseworkers() {
    if (persistedDataGenerator != null) {
      persistedDataGenerator.deleteTrackedData();
    }
  }

  @Test
  void givenAllControllerTestsHaveRun_thenDatabaseIsEmpty() {
    // dbCleanliness is also called in @AfterEach after each test, including
    // the @BeforeEach-seeded caseworkers that are removed by tearDownTrackedData().
    // By the time this test body runs the caseworkers are already gone (they were
    // removed by @BeforeEach / @AfterEach for this test method itself), so the
    // assertion fires against a truly empty database.
    dbCleanliness.assertAllTablesEmpty("full-controller-suite");
  }
}
