package uk.gov.justice.laa.dstew.access.validation;

import java.util.List;

/**
 * Validation exception that holds violations (errors).
 */
public class ValidationException extends RuntimeException {
  private final List<String> errors;

  /**
   * Initialize with exception message and validation error messages.
   *
   * @param message String exception message.
   * @param errors List of validation messages.
   */
  public ValidationException(String message, List<String> errors) {
    super(message);
    this.errors = (errors == null) ? List.of() : List.copyOf(errors);
  }

  /**
   * Initialize with validation error messages.
   *
   * @param errors List of validation messages.
   */
  public ValidationException(List<String> errors) {
    this((errors == null || errors.isEmpty())
        ? "Validation failed"
        : "One or more validation rules were violated", errors);
  }

  /**
   * Get validation error messages.
   *
   * @return List of validation messages.
   */
  public List<String> errors() {
    return errors;
  }
}
