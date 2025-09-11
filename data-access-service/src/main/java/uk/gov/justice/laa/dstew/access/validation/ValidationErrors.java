package uk.gov.justice.laa.dstew.access.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple state of validation of an incoming DTO.
 * Contains list of validation errors. Consider it like a builder for a ValidationException.
 */
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

  ValidationErrors throwIfAny(String message) {
    if (!errors.isEmpty()) {
      throw new ValidationException(message, List.copyOf(errors));
    }
    return this;
  }

  ValidationErrors throwIfAny() {
    if (!errors.isEmpty()) {
      throw new ValidationException(List.copyOf(errors));
    }
    return this;
  }

  List<String> errors() {
    return List.copyOf(errors);
  }
}
