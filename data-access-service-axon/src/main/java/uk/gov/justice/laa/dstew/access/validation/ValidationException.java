package uk.gov.justice.laa.dstew.access.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Validation exception that holds violations (errors). excluded from code coverage as
 * lines/branches not covered are not testable from the services which use it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@ExcludeFromGeneratedCodeCoverage
public class ValidationException extends RuntimeException {
  private final List<String> errors;

  /**
   * Initialize with exception message and validation error messages.
   *
   * @param message String exception message.
   * @param errors List of validation messages.
   */
  @JsonCreator
  public ValidationException(
      @JsonProperty("message") String message, @JsonProperty("errors") List<String> errors) {
    super(message);
    this.errors = (errors == null) ? List.of() : List.copyOf(errors);
  }

  /**
   * Initialize with validation error messages.
   *
   * @param errors List of validation messages.
   */
  public ValidationException(List<String> errors) {
    this(
        (errors == null || errors.isEmpty())
            ? "Validation failed"
            : "One or more validation rules were violated",
        errors);
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
