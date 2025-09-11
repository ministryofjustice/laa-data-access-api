package uk.gov.justice.laa.dstew.access.validation;

/**
 * Utility class with useful static methods for validation.
 */
public final class ValidationUtils {
  private ValidationUtils() {
    // prevent instantiation.
  }

  public static boolean notNull(Boolean bool) {
    return bool != null ? bool : false;
  }

  public static String notNull(String str) {
    return str != null ? str : "";
  }
}
