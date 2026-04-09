package uk.gov.justice.laa.dstew.access.utils.harness;

/**
 * Constants for the harness execution mode controlled by the {@code test.mode} system property.
 *
 * <p>Set unconditionally by the {@code infrastructureTest} Gradle task: {@code systemProperty
 * 'test.mode', 'infrastructure'}. Not set during normal {@code integrationTest} runs, so {@link
 * System#getProperty(String)} returns {@code null} in that case — which is distinct from {@link
 * #INFRASTRUCTURE} and therefore safe to compare with {@link #isInfrastructure()}.
 *
 * <p>Usage:
 *
 * <pre>
 *     if (HarnessMode.isInfrastructure()) { ... }
 * </pre>
 */
public final class HarnessMode {

  /** The system property key that selects the harness execution mode. */
  public static final String PROPERTY = "test.mode";

  /** Value of {@link #PROPERTY} when running against a live deployed environment. */
  public static final String INFRASTRUCTURE = "infrastructure";

  private HarnessMode() {}

  /**
   * Returns {@code true} when the suite is running in infrastructure mode (i.e. {@code
   * -Dtest.mode=infrastructure} is set).
   */
  public static boolean isInfrastructure() {
    return INFRASTRUCTURE.equals(System.getProperty(PROPERTY));
  }
}
