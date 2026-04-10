package uk.gov.justice.laa.dstew.access.validation;

import java.util.ArrayList;
import java.util.List;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Simple state of validation of an incoming DTO. Contains list of validation errors. Consider it
 * like a builder for a ValidationException.
 */
@ExcludeFromGeneratedCodeCoverage
class ValidationErrors {
  private final List<String> errors;

  private ValidationErrors() {
    this.errors = new ArrayList<>();
  }

  static ValidationErrors empty() {
    return new ValidationErrors();
  }

  ValidationErrors add(String error) {
    errors.add(error);
    return this;
  }

  ValidationErrors addIf(boolean condition, String error) {
    return condition ? add(error) : this;
  }

  List<String> errors() {
    return List.copyOf(errors);
  }
}
